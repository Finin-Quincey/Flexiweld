package uob.flexiweld.geom;

import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Represents an intersection between two lines. This class is simply a container for two lines and a point, and exists
 * only as a data type for use by {@link Intersection#intersect(List, List, List)}. This setup allows the geometry of
 * finding the intersections to be decoupled from their processing/usage without losing information about which lines
 * correspond to which intersection point, which would otherwise require additional processing to retrieve.
 * <p></p>
 * {@code Intersection} objects are immutable.
 */
public class Intersection {

	private final Point point;
	private final Line lineA;
	private final Line lineB;

	private Intersection(Line l, Line m, Point p){
		this.lineA = l;
		this.lineB = m;
		this.point = p;
	}

	/** Returns a copy of the {@link Point} where this intersection lies. */
	public Point getPoint(){
		return point.clone(); // Return a copy to ensure immutability
	}

	/** Returns the first {@link Line} on which this intersection lies. */
	public Line getLineA(){
		return lineA; // Lines are already immutable so can be returned directly
	}

	/** Returns the second {@link Line} on which this intersection lies. */
	public Line getLineB(){
		return lineB;
	}

	// Converters

	/** Converts this {@code Intersection} to an array of points. */
	public Point[] toPoints(){
		return new Point[]{lineA.getStart(), lineA.getEnd(), lineB.getStart(), lineB.getEnd(), getPoint()};
	}

	/** Converts the given array of points to a new {@code Intersection}. */
	public static Intersection fromPoints(Point... points){
		if(points.length != 5) throw new IllegalArgumentException("Incorrect number of points, must be exactly 5");
		return new Intersection(new Line(points[0], points[1]), new Line(points[2], points[3]), points[4]);
	}

	/**
	 * Packs the given list of intersections into a matrix of points, ready for transformations to be applied.
	 * @param intersections A list of intersections to be packed
	 * @return The resulting matrix
	 */
	public static MatOfPoint2f pack(List<Intersection> intersections){
		MatOfPoint2f mat = new MatOfPoint2f();
		intersections.forEach(i -> mat.push_back(new MatOfPoint2f(i.toPoints())));
		return mat;
	}

	/**
	 * Unpacks the given matrix of points into a list of intersections. This performs the inverse operation of
	 * {@link Intersection#pack(List)}.
	 * @param mat The matrix to be unpacked
	 * @return The resulting list of intersections
	 */
	public static List<Intersection> unpack(MatOfPoint2f mat){

		Point[] points = mat.toArray();
		if(points.length % 5 != 0) throw new IllegalArgumentException("Number of points in matrix must be a multiple of 5");

		List<Intersection> intersections = new ArrayList<>();
		int i = 0;

		while(i < points.length){
			intersections.add(Intersection.fromPoints(points[i++], points[i++], points[i++], points[i++], points[i++]));
		}

		return intersections;
	}

	/**
	 * Finds intersections between the given list of lines.
	 * @param lines A list of lines whose intersections are to be found
	 * @param intersections A list of intersections to be populated with the intersections found (will be cleared)
	 * @param segments A list of lines to be populated with the line segments between intersections (will be cleared)
	 */
	public static void intersect(List<Line> lines, List<Intersection> intersections, List<Line> segments){

		intersections.clear();
		segments.clear();

		// It's more efficient to do this all in the same loop, otherwise we'd have to go through all the intersections
		// again, find which ones lie on a given line, sort them and find the segments - for every single line.
		// Sometimes efficiency is more important than separation of concerns!

		for(int i = 0; i < lines.size(); i++){ // Don't use enhanced for loop or we'll get a CME

			Line lineA = lines.get(i);

			List<Point> lineAIntersections = new ArrayList<>();

			for(int j = 0; j < lines.size(); j++){

				Line lineB = lines.get(j);

				Point intersection = Line.intersection(lineA, lineB);

				if(intersection != null){

					// Regardless of whether it's already been visited, store the point for segment-finding later
					lineAIntersections.add(intersection);

					if(j >= i){
						// Only add intersections with lines that have already been processed
						intersections.add(new Intersection(lineA, lineB, intersection));
					}
				}
			}

			if(lineAIntersections.isEmpty()) continue;

			if(Math.abs(lineA.gradient()) > 1){
				lineAIntersections.sort(Comparator.comparingDouble(p -> p.y));
			}else{
				lineAIntersections.sort(Comparator.comparingDouble(p -> p.x));
			}

			for(int j = 0; j < lineAIntersections.size() - 1; j++){
				segments.add(new Line(lineAIntersections.get(j), lineAIntersections.get(j + 1)));
			}
		}
	}

}
