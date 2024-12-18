package mindustrytool.mindustrycontentparser.types.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MapPreviewRequest {
    @NotNull
    private byte[] data;

}
