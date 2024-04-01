package com.hanghae.theham.domain.rental.controller.docs;

import com.hanghae.theham.domain.rental.dto.RentalRequestDto.RentalCreateRequestDto;
import com.hanghae.theham.domain.rental.dto.RentalRequestDto.RentalUpdateRequestDto;
import com.hanghae.theham.domain.rental.dto.RentalResponseDto.RentalCreateResponseDto;
import com.hanghae.theham.domain.rental.dto.RentalResponseDto.RentalReadResponseDto;
import com.hanghae.theham.domain.rental.dto.RentalResponseDto.RentalUpdateResponseDto;
import com.hanghae.theham.global.dto.ResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "rentals", description = "함께쓰기 관련 API")
public interface RentalControllerDocs {

    @Operation(summary = "함께쓰기 게시글 등록 기능", description = "함께쓰기 게시글을 등록할 수 있는 API")
    ResponseDto<RentalCreateResponseDto> createRental(
            @RequestPart @Valid RentalCreateRequestDto requestDto,
            @RequestPart(required = false) List<MultipartFile> multipartFileList
    );

    @Operation(summary = "함께쓰기 게시글 조회 기능", description = "함께쓰기 게시글을 조회할 수 있는 API")
    ResponseDto<RentalReadResponseDto> readRental(
            @PathVariable Long rentalId
    );

    @Operation(summary = "함께쓰기 게시글 수정 기능", description = "함께쓰기 게시글을 수정할 수 있는 API")
    ResponseDto<RentalUpdateResponseDto> updateRental(
            @PathVariable Long rentalId,
            @RequestPart @Valid RentalUpdateRequestDto requestDto,
            @RequestPart(required = false) List<MultipartFile> multipartFileList
    );

    @Operation(summary = "함께쓰기 게시글 삭제 기능", description = "함께쓰기 게시글을 삭제할 수 있는 API")
    void deleteRental(
            @PathVariable Long rentalId,
            @RequestParam String email // TODO: 3/31/24 로그인 기능 적용 되면 수정
    );
}