package org.osm2world.core.target.common.material;

import static java.lang.Math.*;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import org.osm2world.core.util.Resolution;

/**
 * two textures (with compatible dimensions) combined with each other
 */
public class CompositeTexture extends RuntimeTexture {

	public enum CompositeMode {
		/** combine {@link #textureA}'s alpha channel with {@link #textureB}'s RGB channels */
		ALPHA_FROM_A,
		/** draw {@link #textureA}, then {@link #textureB} on top */
		STACKED
	}

	private final CompositeMode mode;
	private final boolean rescale;

	private final TextureData textureA;
	private final TextureData textureB;

	public CompositeTexture(CompositeMode mode, boolean rescale, TextureData textureA, TextureData textureB) {
		super(textureA.width, textureA.height, textureA.widthPerEntity, textureA.heightPerEntity,
				textureA.wrap, textureA.coordFunction);
		this.mode = mode;
		this.rescale = rescale;
		this.textureA = textureA;
		this.textureB = textureB;
	}

	@Override
	public BufferedImage getBufferedImage() {

		/* obtain images and dimensions */

		BufferedImage imageA = textureA.getBufferedImage();
	    BufferedImage imageB = textureB.getBufferedImage();

		int imageWidth = imageA.getWidth();
		int imageHeight = imageA.getHeight();

		if (rescale) {

			Resolution resA = Resolution.of(imageA);
			Resolution resB = Resolution.of(imageB);
			Resolution outputRes = new Resolution(max(resA.width, resB.width), max(resA.height, resB.height));

			imageA = textureA.getBufferedImage(outputRes);
			imageB = textureB.getBufferedImage(outputRes);

		} else {

		    /* repeat imageB to fill the necessary space */

		    //TODO: should take TextureData.width and .height into account and scale the image

			if (imageB.getHeight() < imageHeight || imageB.getWidth() < imageWidth) {

				BufferedImage tiledImageB = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
				Graphics2D g2d = tiledImageB.createGraphics();

				for (int repeatX = 0; repeatX < (int)ceil(imageB.getWidth() / imageWidth); repeatX ++) {
					for (int repeatY = 0; repeatY < (int)ceil(imageHeight / imageB.getHeight()); repeatY ++) {
						g2d.drawImage(imageA, imageWidth * repeatX, imageHeight * repeatY, null);
					}
				}

				g2d.dispose();

				imageB = tiledImageB;

			}

		}

		/* combine the images into a result */

		BufferedImage result = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);

		if (mode == CompositeMode.STACKED) {

			Graphics2D g2d = result.createGraphics();
			g2d.drawImage(imageA, 0, 0, imageA.getWidth(), imageA.getHeight(), null);
			g2d.drawImage(imageB, 0, 0, imageB.getWidth(), imageB.getHeight(), null);
			g2d.dispose();

		} else if (mode == CompositeMode.ALPHA_FROM_A) {

			for (int y = 0; y < imageHeight; y++) {
				for (int x = 0; x < imageWidth; x++) {
					Color cA = new Color(imageA.getRGB(x, y), true);
					Color cB = new Color(imageB.getRGB(x, y));
					Color colorWithAlpha = new Color(cB.getRed(), cB.getGreen(), cB.getBlue(), cA.getAlpha());
					result.setRGB(x, y, colorWithAlpha.getRGB());
				}
			}

		} else {
			throw new Error("Unsupported mode " + mode);
		}

		return result;

	}

	@Override
	public String toString() {
		return "CompositeTexture [" + mode + ", " + textureA + " + " + textureB + "]";
	}

	/**
	 * stacks an arbitrary number of textures (compare {@link CompositeMode#STACKED})
	 * @param textures  textures ordered bottom to top, != null
	 */
	public static TextureData stackOf(List<TextureData> textures) {
		switch (textures.size()) {
		case 0: return new BlankTexture();
		case 1: return textures.get(0);
		case 2: return new CompositeTexture(CompositeMode.STACKED, true,
				textures.get(0), textures.get(1));
		default: return new CompositeTexture(CompositeMode.STACKED, true,
				textures.get(0), stackOf(textures.subList(1, textures.size())));
		}
	}

}
