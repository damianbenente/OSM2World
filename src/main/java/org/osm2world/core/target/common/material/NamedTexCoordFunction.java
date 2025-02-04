package org.osm2world.core.target.common.material;

import static java.lang.Math.*;
import static java.lang.Math.max;
import static java.util.Collections.max;
import static java.util.Collections.min;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.List;

import org.osm2world.core.math.AxisAlignedRectangleXZ;
import org.osm2world.core.math.FaceXYZ;
import org.osm2world.core.math.SimplePolygonXZ;
import org.osm2world.core.math.TriangleXYZ;
import org.osm2world.core.math.VectorXYZ;
import org.osm2world.core.math.VectorXZ;
import org.osm2world.core.target.Target;

/**
 * several useful {@link TexCoordFunction} implementations.
 * They can be referenced by name in style definition files.
 * {@link Target}s may also provide special implementations for them
 * (e.g. as a specialized shader).
 */
public enum NamedTexCoordFunction implements TexCoordFunction {

	/**
	 * uses x and z vertex coords together with the texture's width and height
	 * to place a texture. This function works for all geometries,
	 * but steep inclines or even vertical walls produce odd-looking results.
	 */
	GLOBAL_X_Z,

	/**
	 * like {@link #GLOBAL_X_Z}, but uses y instead of z dimension.
	 * Better suited for certain vertical surfaces.
	 */
	GLOBAL_X_Y,

	/**
	 * creates texture coordinates for individual triangles that
	 * orient the texture based on each triangle's downward slope.
	 *
	 * TODO: introduce face requirement?
	 */
	SLOPED_TRIANGLES,

	/**
	 * creates texture coordinates for a triangle strip (alternating between
	 * upper and lower vertex), based on the length along a wall from the
	 * starting point, height of the vertex, and texture size.
	 *
	 * This only works for vertices forming a triangle strip,
	 * alternating between upper and lower vertex.
	 */
	STRIP_WALL,

	/**
	 * creates texture coordinates for a triangle strip (alternating between
	 * upper and lower vertex), based on the length along a wall from the
	 * starting point.
	 *
	 * Similar to {@link #STRIP_WALL}, except that one texture coordinate
	 * dimension alternates between 1 and 0 instead of being based on height.
	 */
	STRIP_FIT_HEIGHT,

	/**
	 * stretches the texture exactly once onto a triangle strip (alternating
	 * between upper and lower vertex).
	 *
	 * Most commonly used to texture a rectangle represented as a
	 * triangle strip with 2 triangles.
	 */
	STRIP_FIT,

	/**
	 * fits an image onto a flat polygon.
	 * Vertices must represent the vertex loop of a {@link FaceXYZ}
	 */
	FACE_FIT;

	@Override
	public List<VectorXZ> apply(List<VectorXYZ> vs, TextureData textureData) {

		List<VectorXZ> result = new ArrayList<>(vs.size());

		switch (this) {

		case GLOBAL_X_Z:
		case GLOBAL_X_Y:

			for (VectorXYZ v : vs) {
				result.add(new VectorXZ(
						v.x / textureData.width,
						(this == GLOBAL_X_Y ? v.y : v.z) / textureData.height));
			}

			break;

		case SLOPED_TRIANGLES:

			if (vs.size() % 3 != 0) {
				throw new IllegalArgumentException("not a set of triangles");
			}

			List<Double> knownAngles = new ArrayList<Double>();

			for (int i = 0; i < vs.size() / 3; i++) {

				//TODO avoid creating a temporary triangle
				TriangleXYZ triangle = new TriangleXYZ(vs.get(3*i), vs.get(3*i+1), vs.get(3*i+2));

				VectorXZ normalXZProjection = triangle.getNormal().xz();

				double downAngle = 0;

				if (normalXZProjection.x != 0 || normalXZProjection.z != 0) {

					downAngle = normalXZProjection.angle();

					//try to avoid differences between triangles of the same face

					Double similarKnownAngle = null;

					for (double knownAngle : knownAngles) {
						if (abs(downAngle - knownAngle) < 0.02) {
							similarKnownAngle = knownAngle;
							break;
						}
					}

					if (similarKnownAngle == null) {
						knownAngles.add(downAngle);
					} else {
						downAngle = similarKnownAngle;
					}

				}

				for (VectorXYZ v : triangle.verticesNoDup()) {
					VectorXZ baseTexCoord = v.rotateY(-downAngle).xz();
					result.add(new VectorXZ(
							-baseTexCoord.x / textureData.width,
							-baseTexCoord.z / textureData.height));
				}

			}

			break;

		case STRIP_WALL:
		case STRIP_FIT_HEIGHT:
		case STRIP_FIT:

			if (vs.size() % 2 == 1) {
				throw new IllegalArgumentException("not a triangle strip wall");
			}

			/* calculate length of the wall (if needed later) */

			double totalLength = 0;

			if (this == STRIP_FIT || textureData.widthPerEntity != null) {
				for (int i = 0; i+1 < vs.size(); i++) {
					totalLength += vs.get(i).distanceToXZ(vs.get(i+1));
				}
			}

			/* calculate number of repetitions in each dimension */

			double width, height;

			if (this == STRIP_FIT) {
				width = totalLength;
			} else if (textureData.widthPerEntity != null) {
				long entities = max(1, round(totalLength / textureData.widthPerEntity));
				double textureRepeats = entities / (textureData.width / textureData.widthPerEntity);
				width = totalLength / textureRepeats;
			} else {
				width = textureData.width;
			}

			if (textureData.heightPerEntity != null) {
				List<Double> yValues = vs.stream().map(v -> v.y).collect(toList());
				double totalHeight = max(yValues) - min(yValues);
				long entities = max(1, round(totalHeight / textureData.heightPerEntity));
				double textureRepeats = entities / (textureData.height / textureData.heightPerEntity);
				height = totalHeight / textureRepeats;
			} else {
				height = textureData.height;
			}

			/* calculate texture coordinate list */

			double accumulatedLength = 0;

			for (int i = 0; i < vs.size(); i++) {

				VectorXYZ v = vs.get(i);

				// increase accumulated length after every second vector

				if (i > 0 && i % 2 == 0) {
					accumulatedLength += v.xz().distanceTo(vs.get(i-2).xz());
				}

				// calculate texture coords

				double s, t;

				s = accumulatedLength / width;

				if (this == STRIP_WALL) {
					t = (i % 2 == 0) ? (v.distanceTo(vs.get(i+1))) / height : 0;
				} else {
					t = (i % 2 == 0) ? 1 : 0;
				}

				result.add(new VectorXZ(s, t));

			}

			break;

		case FACE_FIT:

			FaceXYZ face = new FaceXYZ(vs);
			SimplePolygonXZ faceXZ = face.toFacePlane(face);
			AxisAlignedRectangleXZ faceBbox = faceXZ.boundingBox();

			for (VectorXZ v : faceXZ.vertices()) {
				VectorXZ vRelative = v.subtract(faceBbox.bottomLeft());
				result.add(new VectorXZ(vRelative.x / faceBbox.sizeX(), vRelative.z / faceBbox.sizeZ()));
			}

			break;

		default:

			throw new Error("unimplemented texture coordinate function");

		}

		return result;

	}

}
