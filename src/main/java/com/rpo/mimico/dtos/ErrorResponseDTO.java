package com.rpo.mimico.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponseDTO {

    private String code;
    private String message;
    private Map<String, Object> details;
    private String correlationId;

    public ErrorResponseDTO(String message) {
        this.code = "REQUEST_INVALID";
        this.message = message;
    }
}
