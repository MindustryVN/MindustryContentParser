package mindustrytool.mindustrycontentparser.service;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

import javax.imageio.ImageIO;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import arc.files.Fi;
import arc.graphics.Texture;
import arc.graphics.g2d.TextureAtlas.TextureAtlasData;
import arc.struct.ObjectMap;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mindustrytool.mindustrycontentparser.EnvConfig;
import mindustrytool.mindustrycontentparser.utils.ApiError;
import mindustrytool.mindustrycontentparser.utils.Utils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetsService {

    private final EnvConfig config;

    private static final ObjectMap<String, BufferedImage> cache = new ObjectMap<String, BufferedImage>();

    public void reset() {
        cache.clear();
    }

    @PostConstruct
    public void init() throws InterruptedException, ExecutionException {
        if (!config.init()) {
            return;
        }

        var assetsFolder = new Fi(config.files().assetsFolder() + "images/");

        if (!assetsFolder.exists()) {
            assetsFolder.mkdirs();
        }

        var atlasFile = new Fi(config.files().assetsFolder() + "assets/sprites/sprites.aatls");
        var spriteFolder = new Fi(config.files().assetsFolder() + "assets/sprites");

        var atlas = new TextureAtlasData(atlasFile, spriteFolder, false);

        for (var page : atlas.getPages()) {
            try {

                BufferedImage atlasPage = ImageIO.read(page.textureFile.file());

                page.texture = Texture.createEmpty(null);
                page.texture.width = page.width;
                page.texture.height = page.height;

                for (var region : atlas.getRegions()) {

                    if (page != region.page) {
                        continue;
                    }

                    Utils.EXECUTOR_SERVICE.execute(() -> {

                        BufferedImage image = new BufferedImage(region.width, region.height, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D graphics = image.createGraphics();

                        graphics.drawImage(atlasPage, 0, 0, region.width, region.height, region.left, region.top, region.left + region.width, region.top + region.height, null);

                        try {
                            ImageIO.write(image, "png", getImageFile(region.name).file());
                            log.info("Saved: " + region.name);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });

                }
            } catch (IOException e) {
                log.error("Failed to read: " + page.textureFile.name(), e);
            }
        }
    }

    public BufferedImage getAssetsByName(String assetName) {

        return cache.get(assetName, () -> {

            var image = getImage(assetName);

            if (image != null) {
                return image;
            }

            throw new ApiError(HttpStatus.INTERNAL_SERVER_ERROR, "Error reading assets: " + assetName);
        });
    }

    private String getFileName(String assetName) {
        return config.files().assetsFolder() + "images/" + assetName.replace(" ", "_") + ".png";
    }

    private Fi getImageFile(String assetName) {
        return new Fi(getFileName(assetName));
    }

    private BufferedImage getImage(String assetName) {
        try {
            return ImageIO.read(getImageFile(assetName).file());
        } catch (Exception e) {
            return null;
        }
    }
}
