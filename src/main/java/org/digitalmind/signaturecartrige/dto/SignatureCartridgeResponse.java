package org.digitalmind.signaturecartrige.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.core.io.Resource;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode
@Data
@Schema(description = "The result object containing a signature cartridge image.")
@JsonPropertyOrder(
        {
                "request",
                "contentType",
                "resource"
        }
)
public class SignatureCartridgeResponse {

    @Schema(description = "The cartridge request")
    private SignatureCartridgeRequest request;

    @Schema(description = "The format of the barcode resource")
    private String contentType;

    @Schema(description = "The barcode resource")
    private Resource resource;

}
