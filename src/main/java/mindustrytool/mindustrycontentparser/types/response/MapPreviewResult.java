package mindustrytool.mindustrycontentparser.types.response;

import arc.struct.ObjectMap;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class MapPreviewResult {
    public String name, author, description;
    public int height, width;
    public ObjectMap<String, String> tags = new ObjectMap<>();
    public String image;
}
