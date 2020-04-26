package org.digitalmind.signaturecartrige.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;
import org.springframework.core.io.Resource;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode
@Data
@ApiModel(value = "SignatureCartridgeResponse", description = "The result object containing a signature cartidge image.")
@JsonPropertyOrder(
        {
                "request",
                "contentType",
                "resource"
        }
)
public class SignatureCartridgeResponse {

    @ApiModelProperty(value = "The cartridge request", required = false)
    private SignatureCartridgeRequest request;

    @ApiModelProperty(value = "The format of the barcode resource", required = false)
    private String contentType;

    @ApiModelProperty(value = "The barcode resource", required = false)
    private Resource resource;

}
