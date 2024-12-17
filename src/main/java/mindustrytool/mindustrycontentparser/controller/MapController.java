package mindustrytool.mindustrycontentparser.controller;

import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import mindustrytool.mindustrycontentparser.service.MapService;
import mindustrytool.mindustrycontentparser.types.request.MapPreviewRequest;
import mindustrytool.mindustrycontentparser.types.response.MapPreviewResult;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/maps")
@RequiredArgsConstructor
public class MapController {

    private final MapService mapService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<MapPreviewResult> getPreview(@Validated @ModelAttribute MapPreviewRequest request) {
        return mapService.getPreview(request);
    }
}
