package uob.flexiweld;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.util.function.BiConsumer;

/**
 * Static utility class for various helper methods.
 * @author Finin Quincey
 */
public final class Utils {

	private Utils(){} // No instances!

	/**
	 * Processes the given source image using the given operation, and returns the resulting destination image. This
	 * method eliminates the need for explicit creation of a new destination image variable each time, allowing each
	 * operation to be condensed to a one-liner, much like {@code String} manipulation. For example, a typical call
	 * might look like this:
	 * <p></p>
	 * <center>{@code frame = process(frame, (s, d) -> Imgproc.resize(s, d, new Size(width, height)));}</center>
	 * @param src The source image to be processed
	 * @param operation A {@link BiConsumer} representing the operation to be performed, usually expressed as a lambda
	 *                  expression.
	 * @return The resulting destination image. This may be assigned to the source image variable to overwrite it.
	 */
	public static Mat process(Mat src, BiConsumer<Mat, Mat> operation){
		Mat dest = Mat.zeros(src.rows(), src.cols(), src.type());
		operation.accept(src, dest);
		return dest;
	}

	public static void setupFlipMatrices(Mat mapX, Mat mapY){
		// TODO: Figure out what this is actually doing!
		// (I know what it's doing in theory, but the actual matrix manipulation bit is kinda opaque)
		float[] buffX = new float[(int)(mapX.total() * mapX.channels())];
		mapX.get(0, 0, buffX);
		float[] buffY = new float[(int)(mapY.total() * mapY.channels())];
		mapY.get(0, 0, buffY);

		for(int i = 0; i < mapX.rows(); i++){
			for(int j = 0; j < mapX.cols(); j++){
				buffX[i * mapX.cols() + j] = mapY.cols() - j;
				buffY[i * mapY.cols() + j] = i;
			}
		}

		mapX.put(0, 0, buffX);
		mapY.put(0, 0, buffY);
	}

}
