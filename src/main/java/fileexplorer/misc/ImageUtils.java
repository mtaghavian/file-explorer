package fileexplorer.misc;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class ImageUtils {

    public static BufferedImage scaleImage(BufferedImage img, int targetSize) {
        int imgMin = Math.min(img.getHeight(), img.getWidth());
        double co = (double) targetSize / imgMin;
        int th = (int) Math.ceil(img.getHeight() * co), tw = (int) Math.ceil(img.getWidth() * co);
        Image resultingImage = img.getScaledInstance(tw, th, Image.SCALE_DEFAULT);
        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
        out.getGraphics().drawImage(resultingImage, 0, 0, null);
        return out;
    }

    public static BufferedImage createThumbnail(BufferedImage img, int targetSize) throws IOException {
        img = scaleImage(img, targetSize);
        int cx = img.getWidth() / 2, cy = img.getHeight() / 2;
        img = cropImage(img, new Rectangle(Math.max(0, cx - targetSize / 2), Math.max(0, cy - targetSize / 2), targetSize, targetSize));
        return img;
    }

    public static BufferedImage createThumbnail(File imageFile, int targetSize) throws IOException {
        BufferedImage img = ImageIO.read(imageFile);
        return createThumbnail(img, targetSize);
    }

    public static BufferedImage cropImage(BufferedImage src, Rectangle rect) {
        BufferedImage dest = src.getSubimage(rect.x, rect.y, rect.width, rect.height);
        return dest;
    }

    public static BufferedImage rotate(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        BufferedImage newImage = new BufferedImage(height, width, img.getType());
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                newImage.setRGB(height - 1 - j, i, img.getRGB(i, j));
            }
        }
        return newImage;
    }

    public static BufferedImage merge(List<BufferedImage> images, boolean vertically, boolean drawBorder) {
        int width = 0, height = 0;
        for (int i = 0; i < images.size(); i++) {
            if (vertically) {
                height += images.get(i).getHeight();
                width = Math.max(width, images.get(i).getWidth());
            } else {
                width += images.get(i).getWidth();
                height = Math.max(height, images.get(i).getHeight());
            }
        }
        BufferedImage img = new BufferedImage(width, height, images.get(0).getType());
        Graphics2D graphics = img.createGraphics();
        graphics.setColor(Color.black);
        int x = 0, y = 0;
        for (int i = 0; i < images.size(); i++) {
            graphics.drawImage(images.get(i), x, y, null);
            if (drawBorder) {
                graphics.drawRect(x, y, images.get(i).getWidth(), images.get(i).getHeight());
            }
            if (vertically) {
                y += images.get(i).getHeight();
            } else {
                x += images.get(i).getWidth();
            }
        }
        return img;
    }
}
