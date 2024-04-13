package com.hanghae.theham.domain.chat.service;

import com.hanghae.theham.domain.chat.dto.ChatResponseDto.ChatReadResponseDto;
import com.hanghae.theham.domain.chat.dto.ChatRoomRequestDto.ChatRoomCreateRequestDto;
import com.hanghae.theham.domain.chat.dto.ChatRoomResponseDto.ChatRoomDetailResponseDto;
import com.hanghae.theham.domain.chat.dto.ChatRoomResponseDto.ChatRoomListResponseDto;
import com.hanghae.theham.domain.chat.dto.ChatRoomResponseDto.ChatRoomReadResponseDto;
import com.hanghae.theham.domain.chat.entity.Chat;
import com.hanghae.theham.domain.chat.entity.ChatRoom;
import com.hanghae.theham.domain.chat.repository.ChatRepository;
import com.hanghae.theham.domain.chat.repository.ChatRoomRepository;
import com.hanghae.theham.domain.member.entity.Member;
import com.hanghae.theham.domain.member.repository.MemberRepository;
import com.hanghae.theham.domain.rental.entity.Rental;
import com.hanghae.theham.domain.rental.repository.RentalRepository;
import com.hanghae.theham.global.exception.BadRequestException;
import com.hanghae.theham.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Transactional(readOnly = true)
@Service
public class ChatRoomService {
    private final ChatRoomRepository chatRoomRepository;
    private final MemberRepository memberRepository;
    private final RentalRepository rentalRepository;
    private final ChatRepository chatRepository;

    public ChatRoomService(ChatRoomRepository chatRoomRepository, MemberRepository memberRepository, RentalRepository rentalRepository, ChatRepository chatRepository) {
        this.chatRoomRepository = chatRoomRepository;
        this.memberRepository = memberRepository;
        this.rentalRepository = rentalRepository;
        this.chatRepository = chatRepository;
    }

    @Transactional
    public Long handleChatRoom(String email, ChatRoomCreateRequestDto requestDto) {
        // 렌탈 작성글이 존재하는지 확인
        Rental rental = findRentalById(requestDto.getRentalId());

        // 채팅 요청한 member
        Member sender = findMemberByEmail(email);

        // 채팅 요청 받은 member
        Member receiver = memberRepository.findByNickname(requestDto.getSellerNickname()).orElseThrow(() -> {
            log.error("회원 정보를 찾을 수 없습니다. nickname: {}", requestDto.getSellerNickname());
            return new BadRequestException(ErrorCode.NOT_FOUND_MEMBER.getMessage());
        });

        if (sender.equals(receiver) || rental.getMember().equals(sender)) {
            throw new BadRequestException(ErrorCode.CANNOT_CHAT_WITH_SELF.getMessage());
        }

        Optional<ChatRoom> optionalChatRoom = chatRoomRepository.findChatRoomBySenderAndRental(sender, rental);

        return optionalChatRoom.orElseGet(()
                -> createChatRoom(sender, receiver, rental)).getId();
    }

    @Transactional
    public ChatRoom createChatRoom(Member sender, Member receiver, Rental rental) {
        ChatRoom newRoom = ChatRoom.builder()
                .sender(sender)
                .receiver(receiver)
                .rental(rental)
                .build();
        return chatRoomRepository.save(newRoom);
    }

    // 채팅방 전체 목록 조회
    public ChatRoomReadResponseDto getChatRoomList(String email, int page, int size) {
        Member member = findMemberByEmail(email);

        PageRequest pageRequest = PageRequest.of(Math.max(page - 1, 0), size, Sort.Direction.DESC, "modifiedAt");
        Page<ChatRoom> chatRoomPage = chatRoomRepository.findChatRoomByMember(member, pageRequest);

        List<ChatRoom> chatRooms = chatRoomPage.getContent();
        List<ChatRoomListResponseDto> chatRoomList = new ArrayList<>();

        chatRooms.stream().forEach(chatRoom -> {

            Member toMember = chatRoom.getSender().equals(member) ? chatRoom.getReceiver() : chatRoom.getSender();
            int unreadCount = chatRoom.getSender().equals(member) ? chatRoom.getSenderUnreadCount() : chatRoom.getReceiverUnreadCount();


            chatRoomList.add(new ChatRoomListResponseDto(
                    chatRoom.getId(),
                    toMember.getId(),
                    toMember.getNickname(),
                    toMember.getProfileUrl(),
                    chatRoom.getLastChat(),
                    unreadCount,
                    chatRoom.getModifiedAt()
            ));
        });
        return new ChatRoomReadResponseDto(chatRoomPage.getTotalPages(), chatRoomPage.getNumber() + 1, chatRoomList);
    }

    // 채팅방 상세 조회
    @Transactional
    public ChatRoomDetailResponseDto getChatRoom(String email, Long chatRoomId, int page, int size) {

        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId).orElseThrow(() -> {
            log.error("채팅방 정보를 찾을 수 없습니다. 채팅방 ID: {}", chatRoomId);
            return new BadRequestException(ErrorCode.NOT_FOUND_CHAT_ROOM.getMessage());
        });
        Member member = findMemberByEmail(email); // 현재 접속한 멤버
        Member sender = chatRoom.getSender(); // 채팅방 최초 발신자
        Member receiver = chatRoom.getReceiver(); // 채팅방 최초 수신자

        // 발신자가 최초 발신자와 동일한지 확인
        boolean isSender = chatRoom.getSender().equals(member);

        // 이전 메시지 읽음 처리
        readPreviousMessages(chatRoom, isSender, sender, receiver).forEach(Chat::updateIsRead);

        // 채팅방 업데이트
        chatRoom.updateChatRoom(isSender);

        // 발신자 수신자 프로필 이미지
        String senderProfileImage = member.getProfileUrl();

        // 대화 상대자 정보 가져오기
        Member toMember = isSender ? receiver : sender;

        // 채팅 메시지 가져오기
        PageRequest pageRequest = PageRequest.of(Math.max(page - 1, 0), size, Sort.Direction.DESC, "createdAt");

        Page<Chat> chatPage = chatRepository.findByChatRoom(chatRoom, pageRequest);
        List<ChatReadResponseDto> chatResponseList = chatPage.getContent()
                .stream()
                .map(ChatReadResponseDto::new)
                .toList();

        return new ChatRoomDetailResponseDto(
                chatPage.getTotalPages(),
                chatPage.getNumber() + 1,
                toMember.getNickname(),
                toMember.getProfileUrl(),
                senderProfileImage,
                chatResponseList);
    }

    public List<Chat> readPreviousMessages(ChatRoom chatRoom, boolean isSender, Member sender, Member receiver) {
        if (isSender) {
            // 현재 사용자가 발신자인 경우, 수신자(receiver)가 보낸 읽지 않은 메시지를 가져온다.
            List<Chat> unreadChatList = chatRepository.findByChatRoomAndSenderAndIsRead(chatRoom, receiver, false);
            return unreadChatList;
        }
        // 현재 사용자가 수신자인 경우, 발신자(sender)가 보낸 읽지 않은 메시지를 가져온다.
        List<Chat> unreadChatList = chatRepository.findByChatRoomAndSenderAndIsRead(chatRoom, sender, false);
        return unreadChatList;
    }

    private Member findMemberByEmail(String email) {
        return memberRepository.findByEmail(email).orElseThrow(() -> {
            log.error("회원 정보를 찾을 수 없습니다. 이메일: {}", email);
            return new BadRequestException(ErrorCode.NOT_FOUND_MEMBER.getMessage());
        });
    }

    private Rental findRentalById(Long id) {
        return rentalRepository.findById(id).orElseThrow(() -> {
            log.error("함께쓰기 게시글 정보를 찾을 수 없습니다. 함께쓰기 정보: {}", id);
            return new BadRequestException(ErrorCode.NOT_FOUND_RENTAL.getMessage());
        });
    }
}
