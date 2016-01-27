// ================================================================
//
// Disclaimer: IMPORTANT: This software was developed at the National
// Institute of Standards and Technology by employees of the Federal
// Government in the course of their official duties. Pursuant to
// title 17 Section 105 of the United States Code this software is not
// subject to copyright protection and is in the public domain. This
// is an experimental system. NIST assumes no responsibility
// whatsoever for its use by other parties, and makes no guarantees,
// expressed or implied, about its quality, reliability, or any other
// characteristic. We would appreciate acknowledgement if the software
// is used. This software can be redistributed and/or modified freely
// provided that any derivative works bear some notice that they are
// derived from it, and any modified versions bear some notice that
// they have been modified.
//
// ================================================================

// ================================================================
//
// Author: tjb3
// Date: Aug 1, 2013 4:11:44 PM EST
//
// Time-stamp: <Aug 1, 2013 4:11:44 PM tjb3>
//
//
// ================================================================

package gov.nist.isg.mist.stitching.lib32.imagetile;

import gov.nist.isg.mist.stitching.lib.common.Array2DView;
import gov.nist.isg.mist.stitching.lib.common.CorrelationTriple;
import gov.nist.isg.mist.stitching.lib.imagetile.ImageTile;

//import gov.nist.isg.mist.stitching.lib.imagetile.fftw.FftwImageTile;
//import gov.nist.isg.mist.stitching.lib.imagetile.fftw.FftwStitching;
//import gov.nist.isg.mist.stitching.lib.imagetile.java.JavaImageTile;
//import gov.nist.isg.mist.stitching.lib.imagetile.java.JavaStitching;
//import gov.nist.isg.mist.stitching.lib.imagetile.jcuda.CudaImageTile;
//import gov.nist.isg.mist.stitching.lib.imagetile.jcuda.CudaStitching;

import gov.nist.isg.mist.stitching.lib32.imagetile.java.JavaImageTile32;
import gov.nist.isg.mist.stitching.lib32.imagetile.java.JavaStitching32;
import gov.nist.isg.mist.stitching.lib32.imagetile.fftw.FftwImageTile32;
import gov.nist.isg.mist.stitching.lib32.imagetile.fftw.FftwStitching32;
import gov.nist.isg.mist.stitching.lib32.imagetile.jcuda.CudaImageTile32;
import gov.nist.isg.mist.stitching.lib32.imagetile.jcuda.CudaStitching32;
import gov.nist.isg.mist.stitching.lib32.imagetile.memory.CudaTileWorkerMemory32;
import gov.nist.isg.mist.stitching.lib32.imagetile.memory.FftwTileWorkerMemory32;
import gov.nist.isg.mist.stitching.lib.imagetile.memory.TileWorkerMemory;
import gov.nist.isg.mist.stitching.lib.log.Log;
import gov.nist.isg.mist.stitching.lib.log.Log.LogType;
import gov.nist.isg.mist.stitching.lib.memorypool.CudaAllocator;
import gov.nist.isg.mist.stitching.lib.memorypool.DynamicMemoryPool;
import gov.nist.isg.mist.stitching.lib.tilegrid.TileGrid;
import gov.nist.isg.mist.stitching.lib.tilegrid.traverser.TileGridTraverser;
import jcuda.Sizeof;
import jcuda.driver.*;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility functions for stitching image tiles.
 *
 * @author Tim Blattner
 * @version 1.0
 */
public class Stitching32 {

  /**
   * Whether to use hillclimbing or not
   */
  public static boolean USE_HILLCLIMBING = true;

  // use an exhaustive search of +-(2r+1) instead of hill climbing
  // This is a debugging tool, it is computationally very expensive
  public static boolean USE_EXHAUSTIVE_INSTEAD_OF_HILLCLIMB_SEARCH = false;


  /**
   * The number of FFT peaks to check
   */
  public static int NUM_PEAKS = 2;

  /**
   * The correlation threshold for checking the number of peaks
   */
  @Deprecated
  public static final float CORR_THRESHOLD = 0.9f;

  /**
   * Defintes hill climbing direction using cartesian coordinates when observering a two dimensional
   * grid where the upper left corner is 0,0. Moving north -1 in the y-direction, south +1 in the
   * y-direction, west -1 in the x-direction, and east +1 in the x-direction.
   *
   * @author Tim Blattner
   * @version 1.0
   */
  enum HillClimbDirection {
    North(0, -1), South(0, 1), East(1, 0), West(-1, 0), NorthEast(1, -1), NorthWest(-1, -1), SouthEast(
        1, 1), SouthWest(-1, 1), NoMove(0, 0);

    private int xDir;
    private int yDir;

    private HillClimbDirection(int x, int y) {
      this.xDir = x;
      this.yDir = y;
    }

    public int getXDir() {
      return this.xDir;
    }

    public int getYDir() {
      return this.yDir;
    }

  }

  /**
   * Computes the phase correlation between two images
   *
   * @param t1     the neighboring tile
   * @param t2     the current tile
   * @param memory the tile worker memory
   * @return the correlation triple between these two tiles
   */
  public static <T> CorrelationTriple phaseCorrelationImageAlignment(ImageTile<T> t1,
                                                                     ImageTile<T> t2, TileWorkerMemory memory) throws FileNotFoundException {

    if (t1 instanceof JavaImageTile32)
      return JavaStitching32.phaseCorrelationImageAlignment((JavaImageTile32) t1, (JavaImageTile32) t2,
          memory);
    else if (t1 instanceof FftwImageTile32)
      return FftwStitching32.phaseCorrelationImageAlignment((FftwImageTile32) t1, (FftwImageTile32) t2,
          memory);
    else if (t1 instanceof CudaImageTile32)
      return CudaStitching32.phaseCorrelationImageAlignment((CudaImageTile32) t1, (CudaImageTile32) t2,
          memory, null);
    else
      return null;
  }


  /**
   * Computes the phase correlation between two images using Java
   *
   * @param t1     the neighboring tile
   * @param t2     the current tile
   * @param memory the tile worker memory
   * @return the correlation triple between these two tiles
   */
  public static CorrelationTriple phaseCorrelationImageAlignmentJava(JavaImageTile32 t1,
                                                                     JavaImageTile32 t2,
                                                                     TileWorkerMemory memory) throws FileNotFoundException {
    return JavaStitching32.phaseCorrelationImageAlignment(t1, t2, memory);
  }


  /**
   * Computes the phase correlation between two images using FFTW32
   *
   * @param t1     the neighboring tile
   * @param t2     the current tile
   * @param memory the tile worker memory
   * @return the correlation triple between these two tiles
   */
  public static CorrelationTriple phaseCorrelationImageAlignmentFftw(FftwImageTile32 t1,
                                                                     FftwImageTile32 t2,
                                                                     TileWorkerMemory memory) throws FileNotFoundException {
    return FftwStitching32.phaseCorrelationImageAlignment(t1, t2, memory);
  }

  /**
   * Computes the phase correlation between images using CUDA
   *
   * @param t1     the neighboring tile
   * @param t2     the current tile
   * @param memory the tile worker memory
   * @param stream the CUDA stream
   * @return the correlation triple between these two tiles
   */
  public static CorrelationTriple phaseCorrelationImageAlignmentCuda(CudaImageTile32 t1,
                                                                     CudaImageTile32 t2,
                                                                     TileWorkerMemory memory,
                                                                     CUstream stream) throws FileNotFoundException {
    return CudaStitching32.phaseCorrelationImageAlignment(t1, t2, memory, stream);
  }


  /**
   * Stitching a grid of tiles using a traverser
   *
   * @param traverser the traverser on how to traverse the grid
   * @param grid      the grid of tiles to stitch
   */
  public static <T> void stitchGridFftw(TileGridTraverser<ImageTile<T>> traverser,
                                        TileGrid<ImageTile<T>> grid) throws FileNotFoundException {
    TileWorkerMemory memory = null;
    for (ImageTile<?> t : traverser) {
      t.setThreadID(0);
      t.readTile();

      if (memory == null)
        memory = new FftwTileWorkerMemory32(t);

      int row = t.getRow();
      int col = t.getCol();

      t.computeFft();

      if (col > grid.getStartCol()) {
        ImageTile<?> west = grid.getTile(row, col - 1);
        t.setWestTranslation(Stitching32.phaseCorrelationImageAlignmentFftw((FftwImageTile32) west,
            (FftwImageTile32) t, memory));

        Log.msgNoTime(LogType.HELPFUL,
            " pciam_W(\"" + t.getFileName() + "\",\"" + west.getFileName() + "\"): "
                + t.getWestTranslation());

        t.decrementFftReleaseCount();
        west.decrementFftReleaseCount();

        if (t.getFftReleaseCount() == 0)
          t.releaseFftMemory();

        if (west.getFftReleaseCount() == 0)
          west.releaseFftMemory();

      }

      if (row > grid.getStartRow()) {
        ImageTile<?> north = grid.getTile(row - 1, col);

        t.setNorthTranslation(Stitching32.phaseCorrelationImageAlignmentFftw((FftwImageTile32) north,
            (FftwImageTile32) t, memory));

        Log.msgNoTime(LogType.HELPFUL,
            " pciam_N(\"" + north.getFileName() + "\",\"" + t.getFileName() + "\"): "
                + t.getNorthTranslation());

        t.decrementFftReleaseCount();
        north.decrementFftReleaseCount();

        if (t.getFftReleaseCount() == 0)
          t.releaseFftMemory();

        if (north.getFftReleaseCount() == 0)
          north.releaseFftMemory();

      }
    }

  }

  /**
   * Stitching a grid of tiles using a traverser
   *
   * @param traverser the traverser on how to traverse the grid
   * @param grid      the grid of tiles to stitch
   * @param context   the GPU context
   */
  public static void stitchGridCuda(TileGridTraverser<ImageTile<CUdeviceptr>> traverser,
                                    TileGrid<ImageTile<CUdeviceptr>> grid, CUcontext context) throws FileNotFoundException {
    TileWorkerMemory memory = null;
    DynamicMemoryPool<CUdeviceptr> memoryPool = null;

    JCudaDriver.cuCtxSetCurrent(context);

    int dev = 0;
    CUstream stream = new CUstream();
    JCudaDriver.cuStreamCreate(stream, CUstream_flags.CU_STREAM_DEFAULT);

    CudaImageTile32.bindBwdPlanToStream(stream, dev);
    CudaImageTile32.bindFwdPlanToStream(stream, dev);

    double pWidth = grid.getExtentWidth();
    double pHeight = grid.getExtentHeight();
    // TODO work out why there is a +20 at the end of this math
    int memoryPoolSize = (int) Math.ceil(Math.sqrt(pWidth * pWidth + pHeight * pHeight)) + 20;

    for (ImageTile<CUdeviceptr> t : traverser) {
      t.setDev(dev);
      t.setThreadID(0);

      t.readTile();

      if (memoryPool == null) {
        int[] size = {CudaImageTile32.fftSize * Sizeof.FLOAT * 2};

        memoryPool =
            new DynamicMemoryPool<CUdeviceptr>(memoryPoolSize, false, new CudaAllocator(), size);
      }

      if (memory == null)
        memory = new CudaTileWorkerMemory32(t);

      int row = t.getRow();
      int col = t.getCol();

      t.allocateFftMemory(memoryPool);

      t.computeFft(memoryPool, memory, stream);

      if (col > grid.getStartCol()) {
        ImageTile<CUdeviceptr> west = grid.getTile(row, col - 1);
        t.setWestTranslation(Stitching32.phaseCorrelationImageAlignmentCuda((CudaImageTile32) west,
            (CudaImageTile32) t, memory, stream));

        Log.msg(LogType.HELPFUL, " pciam_W(\"" + t.getFileName() + "\",\"" + west.getFileName()
            + "\"): " + t.getWestTranslation());

        t.decrementFftReleaseCount();
        west.decrementFftReleaseCount();

        if (west.getFftReleaseCount() == 0) {
          west.releaseFftMemory(memoryPool);
        }
      }

      if (row > grid.getStartRow()) {
        ImageTile<CUdeviceptr> north = grid.getTile(row - 1, col);

        t.setNorthTranslation(Stitching32.phaseCorrelationImageAlignmentCuda((CudaImageTile32) north,
            (CudaImageTile32) t, memory, stream));

        Log.msg(LogType.HELPFUL, " pciam_N(\"" + north.getFileName() + "\",\"" + t.getFileName()
            + "\"): " + t.getNorthTranslation());

        t.decrementFftReleaseCount();
        north.decrementFftReleaseCount();

        if (north.getFftReleaseCount() == 0)
          north.releaseFftMemory(memoryPool);
      }

      if (t.getFftReleaseCount() == 0)
        t.releaseFftMemory(memoryPool);
    }

  }

  /**
   * Prints the absolute positions of all tiles in a grid. Requires logging level of helpful.
   *
   * @param grid the grid of tiles to print their absolute positions
   */
  public static <T> void printAbsolutePositions(TileGrid<ImageTile<T>> grid) {
    for (int r = 0; r < grid.getExtentHeight(); r++) {
      for (int c = 0; c < grid.getExtentWidth(); c++) {
        ImageTile<T> t = grid.getSubGridTile(r, c);

        Log.msg(LogType.HELPFUL, t.getFileName() + ": (" + t.getAbsXPos() + ", " + t.getAbsYPos()
            + ") corr: " + t.getTileCorrelation());

      }
    }
  }

  /**
   * Prints the relative displacements of all tiles in a grid. Requires logging level of helpful.
   *
   * @param grid the grid of tiles to print their relative displacements
   */
  public static <T> void printRelativeDisplacements(TileGrid<ImageTile<T>> grid) {
    for (int r = 0; r < grid.getExtentHeight(); r++) {
      for (int c = 0; c < grid.getExtentWidth(); c++) {
        ImageTile<T> t = grid.getSubGridTile(r, c);

        if (c > 0) {
          ImageTile<T> west = grid.getSubGridTile(r, c - 1);

          Log.msg(LogType.HELPFUL, " pciam_W(\"" + t.getFileName() + "\",\"" + west.getFileName()
              + "\"): " + t.getWestTranslation());

        }

        if (r > 0) {
          ImageTile<T> north = grid.getSubGridTile(r - 1, c);

          Log.msg(LogType.HELPFUL, " pciam_N(\"" + north.getFileName() + "\",\"" + t.getFileName()
              + "\"): " + t.getNorthTranslation());

        }
      }
    }
  }

  /**
   * Prints the absolute positions of all tiles in a grid. Requires logging level of helpful.
   *
   * @param grid the grid of tiles to print their absolute positions
   * @param file the file to save the absolute positions
   */
  public static <T> void outputAbsolutePositions(TileGrid<ImageTile<T>> grid, File file) {
    Log.msg(LogType.MANDATORY, "Writing global positions to: " + file.getAbsolutePath());

    try {
      String newLine = "\n";
      FileWriter writer = new FileWriter(file);

      for (int r = 0; r < grid.getExtentHeight(); r++) {
        for (int c = 0; c < grid.getExtentWidth(); c++) {
          ImageTile<T> t = grid.getSubGridTile(r, c);

          writer.write("file: " + t.getFileName() + "; corr: " + t.getTileCorrelationStr()
              + "; position: (" + t.getAbsXPos() + ", " + t.getAbsYPos() + "); grid: ("
              + t.getCol() + ", " + t.getRow() + ");" + newLine);
        }
      }

      writer.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Parses an absolute displacement file into an TileGrid
   *
   * @param grid the grid of tiles
   * @param file the absolute position file
   * @return true if the parsing was successful, otherwise false
   */
  public static <T> boolean parseAbsolutePositions(TileGrid<ImageTile<T>> grid, File file) {
    // Read file line by line and parse as key-value pair
    boolean parseError = false;
    try {
      FileReader reader = new FileReader(file);

      BufferedReader br = new BufferedReader(reader);

      String line;

      String patternStr = "(\\S+): (\\S+|\\(\\S+, \\S+\\));";
      Pattern pattern = Pattern.compile(patternStr);


      while ((line = br.readLine()) != null) {
        String tileName = "";
        double corr = 0.0;
        int xPos = 0;
        int yPos = 0;
        int gridRow = 0;
        int gridCol = 0;
        Matcher matcher = pattern.matcher(line);
        if (!matcher.find()) {
          Log.msg(LogType.MANDATORY, "Error: unable to parse line: " + line);
          Log.msg(LogType.MANDATORY, "Error parsing absolute positions: " + file.getAbsolutePath());
          br.close();
          return false;
        }

        matcher.reset();
        while (matcher.find()) {
          if (matcher.groupCount() == 2) {
            String key = matcher.group(1);
            String value = matcher.group(2);

            if (key.equals("file")) {
              tileName = value;
            } else if (key.equals("corr")) {
              try {
                corr = Double.parseDouble(value);
              } catch (NumberFormatException e) {
                Log.msg(LogType.MANDATORY, "Unable to parse correlation for " + tileName);
                parseError = true;
              }
            } else if (key.equals("position")) {
              value = value.replace("(", "");
              value = value.replace(")", "");
              String[] posSplit = value.split(",");
              try {
                xPos = Integer.parseInt(posSplit[0].trim());
                yPos = Integer.parseInt(posSplit[1].trim());
              } catch (NumberFormatException e) {
                Log.msg(LogType.MANDATORY, "Unable to parse position for " + tileName);
                parseError = true;
              }
            } else if (key.equals("grid")) {
              value = value.replace("(", "");
              value = value.replace(")", "");
              String[] gridSplit = value.split(",");
              try {
                gridCol = Integer.parseInt(gridSplit[0].trim());
                gridRow = Integer.parseInt(gridSplit[1].trim());
              } catch (NumberFormatException e) {
                Log.msg(LogType.MANDATORY, "Unable to parse grid position for " + tileName);
                parseError = true;
              }
            } else {
              Log.msg(LogType.MANDATORY, "Error: Unknown key: " + key);
              parseError = true;
              break;
            }
          } else {
            Log.msg(LogType.MANDATORY, "Error: unable to parse line: " + line);
            parseError = true;
            break;
          }
        }

        if (parseError) {
          Log.msg(LogType.MANDATORY, "Error parsing absolute positions: " + file.getAbsolutePath());
          br.close();
          return false;
        }

        // Get the tile at grid position and set the necessary values
        if (grid != null && !parseError) {
          ImageTile<T> tile = grid.getTile(gridRow, gridCol);
          tile.setAbsXPos(xPos);
          tile.setAbsYPos(yPos);
          tile.setTileCorrelation(corr);
          // tile.set
        }

      }

      reader.close();
      br.close();
    } catch (FileNotFoundException e) {
      Log.msg(LogType.MANDATORY, "Unable to find file: " + file.getAbsolutePath());
    } catch (IOException e) {
      e.printStackTrace();
    }

    return true;
  }

  /**
   * Prints the relative displacements of all tiles in a grid. Requires logging level of helpful.
   *
   * @param grid the grid of tiles to print their relative displacements
   * @param file the file to output the relative displacements
   */
  public static <T> void outputRelativeDisplacements(TileGrid<ImageTile<T>> grid, File file) {
    Log.msg(LogType.MANDATORY, "Writing relative positions to: " + file.getAbsolutePath());
    try {
      String newLine = "\n";
      FileWriter writer = new FileWriter(file);

      for (int r = 0; r < grid.getExtentHeight(); r++) {
        for (int c = 0; c < grid.getExtentWidth(); c++) {
          ImageTile<T> t = grid.getSubGridTile(r, c);

          if (c > 0) {

            ImageTile<T> west = grid.getSubGridTile(r, c - 1);
            writer.write("west, " + t.getFileName() + ", " + west.getFileName() + ", "
                + t.getWestTranslation().toCSVString() + newLine);

          }

          if (r > 0) {
            ImageTile<T> north = grid.getSubGridTile(r - 1, c);
            writer.write("north, " + t.getFileName() + ", " + north.getFileName() + ", "
                + t.getNorthTranslation().toCSVString() + newLine);
          }
        }
      }

      writer.close();

    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Prints the relative displacements of all tiles in a grid. Requires logging level of helpful.
   *
   * @param grid the grid of tiles to print their relative displacements
   * @param file the file to output the relative displacements no optimization
   */
  public static <T> void outputRelativeDisplacementsNoOptimization(TileGrid<ImageTile<T>> grid,
                                                                   File file) {
    Log.msg(LogType.MANDATORY, "Writing relative positions " + "(no optimization) to: " + file.getAbsolutePath());
    try {
      String newLine = "\n";
      FileWriter writer = new FileWriter(file);

      for (int r = 0; r < grid.getExtentHeight(); r++) {
        for (int c = 0; c < grid.getExtentWidth(); c++) {
          ImageTile<T> t = grid.getSubGridTile(r, c);

          if (c > 0) {
            ImageTile<T> west = grid.getSubGridTile(r, c - 1);
            writer.write("west, " + t.getFileName() + ", " + west.getFileName() + ", "
                + t.getPreOptimizationWestTranslation().toCSVString() + newLine);
          }

          if (r > 0) {
            ImageTile<T> north = grid.getSubGridTile(r - 1, c);
            writer.write("north, " + t.getFileName() + ", " + north.getFileName() + ", "
                + t.getPreOptimizationNorthTranslation().toCSVString() + newLine);
          }
        }
      }

      writer.close();

    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Complex the peak cross correlation (up/down) between two images. Given an x,y position, we
   * analyze the 4 possible positions relative to eachother: { {y, x}, {y, w - x}, {h - y, x}, {h -
   * y, w - x}};
   *
   * @param t1 image 1 (neighbor)
   * @param t2 image 2 (current)
   * @param x  the x max position
   * @param y  the y max position
   * @return the relative displacement along the x and y axis and the correlation
   */
  public static CorrelationTriple peakCrossCorrelationUD(ImageTile<?> t1, ImageTile<?> t2, int x,
                                                         int y) {
    int w = t1.getWidth();
    int h = t1.getHeight();
    List<CorrelationTriple> corrList = new ArrayList<CorrelationTriple>();

    int[][] dims = {{y, x}, {y, w - x}, {h - y, x}, {h - y, w - x}};

    for (int i = 0; i < 4; i++) {
      int nr = dims[i][0];
      int nc = dims[i][1];

      Array2DView a1 = new Array2DView(t1, nr, h - nr, nc, w - nc);
      Array2DView a2 = new Array2DView(t2, 0, h - nr, 0, w - nc);

      float peak = crossCorrelation(a1, a2);

      if (Float.isNaN(peak) || Float.isInfinite(peak)) {
        peak = -1.0f;
      }

      corrList.add(new CorrelationTriple(peak, nc, nr));
    }

    for (int i = 0; i < 4; i++) {
      int nr = dims[i][0];
      int nc = dims[i][1];

      Array2DView a1 = new Array2DView(t1, nr, h - nr, 0, w - nc);
      Array2DView a2 = new Array2DView(t2, 0, h - nr, nc, w - nc);

      float peak = crossCorrelation(a1, a2);

      if (Float.isNaN(peak) || Float.isInfinite(peak)) {
        peak = -1.0f;
      }

      corrList.add(new CorrelationTriple(peak, -nc, nr));

    }

    if (corrList.size() == 0)
      return new CorrelationTriple(Float.NEGATIVE_INFINITY, 0, 0);

    return Collections.max(corrList);
  }

  /**
   * Wrapper that computes the up/down CCF at a given x and y location between two image tile's
   *
   * @param x  the x location
   * @param y  the y location
   * @param i1 the first image tile (north/west neighbor)
   * @param i2 the second image tile (current)
   * @return the CorrelationTriple from position x,y
   */
  public static CorrelationTriple computeCCF_UD(int x, int y, ImageTile<?> i1, ImageTile<?> i2) {
    return computeCCF_UD(x, x, y, y, i1, i2);
  }

  /**
   * Computes the up/down CCF values inside a bounding box, returning the best CCF value (one with
   * the highest correlation)
   *
   * @param minBoundX the minimum x value of the bounding box
   * @param maxBoundX the maximum x value of the bounding box
   * @param minBoundY the minimum y value of the bounding box
   * @param maxBoundY the maximum y value of the bounding box
   * @param i1        the first image for CCF computation (north/west neighbor)
   * @param i2        the second image for CCF computation (current)
   * @return the highest CorrelationTriple within the bounding box
   */
  public static CorrelationTriple computeCCF_UD(int minBoundX, int maxBoundX, int minBoundY,
                                                int maxBoundY, ImageTile<?> i1, ImageTile<?> i2) {
    int width = i1.getWidth();
    int height = i1.getHeight();

    float maxPeak = Float.NEGATIVE_INFINITY;
    int x = minBoundX;
    int y = minBoundY;

    for (int i = minBoundY; i <= maxBoundY; i++) {
      for (int j = minBoundX; j <= maxBoundX; j++) {
        Array2DView a1, a2;
        float peak;

        if (j >= 0) {
          a1 = new Array2DView(i1, i, height - i, j, width - j);
          a2 = new Array2DView(i2, 0, height - i, 0, width - j);
        } else {
          a1 = new Array2DView(i1, i, height - i, 0, width + j);
          a2 = new Array2DView(i2, 0, height - i, -j, width + j);
        }

        peak = Stitching32.crossCorrelation(a1, a2);
        if (peak > maxPeak) {
          x = j;
          y = i;
          maxPeak = peak;
        }
      }
    }

    if (Float.isInfinite(maxPeak)) {
      x = minBoundX;
      y = minBoundY;
      maxPeak = -1.0f;
    }

    return new CorrelationTriple(maxPeak, x, y);
  }

  /**
   * Computes the up/down CCF values inside a bounding box, returning the best CCF value (one with
   * the highest correlation)
   *
   * @param minBoundX the minimum x value of the bounding box
   * @param maxBoundX the maximum x value of the bounding box
   * @param minBoundY the minimum y value of the bounding box
   * @param maxBoundY the maximum y value of the bounding box
   * @param i1        the first image for CCF computation (north/west neighbor)
   * @param i2        the second image for CCF computation (current)
   * @param fileStr   the file string to save the CCF
   * @return the highest correlation triple within the bounding box
   */
  public static CorrelationTriple computeCCF_UDAndSave(int minBoundX, int maxBoundX, int minBoundY,
                                                       int maxBoundY, ImageTile<?> i1, ImageTile<?> i2, String fileStr) {

    File file = new File(fileStr);
    int width = i1.getWidth();
    int height = i1.getHeight();

    float maxPeak = Float.NEGATIVE_INFINITY;
    int x = minBoundX;
    int y = minBoundY;

    try {
      FileWriter writer = new FileWriter(file);

      for (int i = minBoundY; i <= maxBoundY; i++) {
        for (int j = minBoundX; j <= maxBoundX; j++) {
          Array2DView a1, a2;
          float peak;

          if (j >= 0) {
            a1 = new Array2DView(i1, i, height - i, j, width - j);
            a2 = new Array2DView(i2, 0, height - i, 0, width - j);
          } else {
            a1 = new Array2DView(i1, i, height - i, 0, width + j);
            a2 = new Array2DView(i2, 0, height - i, -j, width + j);
          }

          peak = Stitching32.crossCorrelation(a1, a2);
          writer.write(peak + ",");
          if (peak > maxPeak) {
            x = j;
            y = i;
            maxPeak = peak;
          }

        }
        writer.write("\n");
      }

      writer.close();
    } catch (IOException e) {
      e.printStackTrace();
    }

    if (Float.isInfinite(maxPeak)) {
      x = minBoundX;
      y = minBoundY;
      maxPeak = -1.0f;
    }

    return new CorrelationTriple(maxPeak, x, y);
  }

  /**
   * Computes cross correlation search with hill climbing (up-down)
   *
   * @param minBoundX min x boundary
   * @param maxBoundX max x boundary
   * @param minBoundY min y boundary
   * @param maxBoundY max y bounadary
   * @param startX    start x position for hill climb
   * @param startY    start y position for hill climb
   * @param i1        the first image for CCF computation (north/west neighbor)
   * @param i2        the second image for CCF computation (current)
   * @return the highest correlation triple within the bounding box using hill climbing
   */
  public static <T> CorrelationTriple computeCCF_HillClimbing_UD(int minBoundX, int maxBoundX,
                                                                 int minBoundY, int maxBoundY,
                                                                 int startX, int startY,
                                                                 ImageTile<T> i1, ImageTile<T> i2) {
    int width = i1.getWidth();
    int height = i1.getHeight();

    int curX = startX;
    int curY = startY;
    float curPeak = Float.NaN;


    minBoundY = Math.max(minBoundY, 0);
    minBoundY = Math.min(minBoundY, height);

    maxBoundY = Math.max(maxBoundY, 0);
    maxBoundY = Math.min(maxBoundY, height);

    minBoundX = Math.max(minBoundX, -width);
    minBoundX = Math.min(minBoundX, width);

    maxBoundX = Math.max(maxBoundX, -width);
    maxBoundX = Math.min(maxBoundX, width);


    // create array of peaks +1 for inclusive, +2 for each end
    int yLength = maxBoundY - minBoundY + 1 + 2;
    int xLength = maxBoundX - minBoundX + 1 + 2;

    float[][] peaks = new float[yLength][xLength];

    boolean foundPeak = false;

    // Compute hill climbing
    while (!foundPeak && ((curX <= maxBoundX && curX >= minBoundX) || (curY <= maxBoundY && curY >= minBoundY))) {

      // translate to 0-based index coordinates
      int curYIndex = curY - minBoundY;
      int curXIndex = curX - minBoundX;

      // check current
      if (Float.isNaN(curPeak)) {
        curPeak = getCCFUD(i1, i2, curX, curY, height, width);
        peaks[curYIndex][curXIndex] = curPeak;
      }

      HillClimbDirection direction = HillClimbDirection.NoMove;

      // Check each direction and move based on highest correlation
      for (HillClimbDirection dir : HillClimbDirection.values()) {
        // Skip NoMove direction
        if (dir == HillClimbDirection.NoMove)
          continue;

        float peak = Float.NEGATIVE_INFINITY;

        // Check if moving dir is in bounds
        if (curY + dir.getYDir() >= minBoundY && curY + dir.getYDir() <= maxBoundY
            && curX + dir.getXDir() >= minBoundX && curX + dir.getXDir() <= maxBoundX) {

          // Check if we have already computed the peak at dir
          if (peaks[curYIndex + dir.getYDir()][curXIndex + dir.getXDir()] == 0.0) {
            peak = getCCFUD(i1, i2, curX + dir.getXDir(), curY + dir.getYDir(), height, width);


            peaks[curYIndex + dir.getYDir()][curXIndex + dir.getXDir()] = peak;
          } else {
            peak = peaks[curYIndex + dir.getYDir()][curXIndex + dir.getXDir()];
          }

          // check if dir gives us the best peak
          if (peak > curPeak) {
            curPeak = peak;
            direction = dir;
          }
        }
      }

      // if the direction did not move, then we are done
      if (direction == HillClimbDirection.NoMove) {
        foundPeak = true;
      } else {
        curX += direction.getXDir();
        curY += direction.getYDir();
      }
    }

    if (Float.isInfinite(curPeak)) {
      curX = minBoundX;
      curY = minBoundY;
      curPeak = -1.0f;
    }

    return new CorrelationTriple(curPeak, curX, curY);
  }


  /**
   * Computes cross correlation search with hill climbing (up-down)
   *
   * @param minBoundX min x boundary
   * @param maxBoundX max x boundary
   * @param minBoundY min y boundary
   * @param maxBoundY max y bounadary
   * @param startX    start x position for hill climb
   * @param startY    start y position for hill climb
   * @param i1        the first image for CCF computation (north/west neighbor)
   * @param i2        the second image for CCF computation (current)
   * @return the highest correlation triple within the bounding box using hill climbing
   */
  public static <T> CorrelationTriple computeCCF_Exhaustive_UD(int minBoundX, int maxBoundX,
                                                               int minBoundY, int maxBoundY,
                                                               int startX, int startY,
                                                               ImageTile<T> i1, ImageTile<T> i2) {
    int width = i1.getWidth();
    int height = i1.getHeight();

    int maxX = startX;
    int maxY = startY;
    float curPeak = Float.NaN;
    float maxPeak = Float.NEGATIVE_INFINITY;

    minBoundY = Math.max(minBoundY, 0);
    minBoundY = Math.min(minBoundY, height);

    maxBoundY = Math.max(maxBoundY, 0);
    maxBoundY = Math.min(maxBoundY, height);

    minBoundX = Math.max(minBoundX, -width);
    minBoundX = Math.min(minBoundX, width);

    maxBoundX = Math.max(maxBoundX, -width);
    maxBoundX = Math.min(maxBoundX, width);


    for (int curX = minBoundX; curX <= maxBoundX; curX++) {
      for (int curY = minBoundY; curY <= maxBoundY; curY++) {

        curPeak = getCCFUD(i1, i2, curX, curY, height, width);
        if (curPeak >= maxPeak) {
          maxPeak = curPeak;
          maxX = curX;
          maxY = curY;
        }

      }
    }

    if (Float.isNaN(maxPeak) || Float.isInfinite(curPeak)) {
      maxX = startX;
      maxY = startY;
      maxPeak = -1.0f;
    }

    return new CorrelationTriple(maxPeak, maxX, maxY);
  }


  /**
   * Computes the cross correlation function (up-down)
   *
   * @param i1     the first image for CCF computation (north/west neighbor)
   * @param i2     the second image for CCF computation (current)
   * @param x      the x position
   * @param y      the y position
   * @param height the height of the image
   * @param width  the width of the image
   * @return the correlation
   */
  public static float getCCFUD(ImageTile<?> i1, ImageTile<?> i2, int x, int y, int height,
                               int width) {
    Array2DView a1, a2;

    if (y < 0)
      y = 0;

    if (x >= 0) {
      a1 = new Array2DView(i1, y, height - y, x, width - x);
      a2 = new Array2DView(i2, 0, height - y, 0, width - x);

    } else {
      a1 = new Array2DView(i1, y, height - y, 0, width + x);
      a2 = new Array2DView(i2, 0, height - y, -x, width + x);
    }

    return Stitching32.crossCorrelation(a1, a2);
  }


  /**
   * Complex the peak cross correlation (left/right) between two images
   *
   * @param t1 image 1 (neighbor)
   * @param t2 image 2 (current)
   * @param x  the x max position
   * @param y  the y max position
   * @return the relative displacement along the x and y axis and the correlation
   */
  public static CorrelationTriple peakCrossCorrelationLR(ImageTile<?> t1, ImageTile<?> t2, int x,
                                                         int y) {
    int w = t1.getWidth();
    int h = t1.getHeight();
    List<CorrelationTriple> corrList = new ArrayList<CorrelationTriple>();

    int[][] dims = {{y, x}, {y, w - x}, {h - y, x}, {h - y, w - x}};

    for (int i = 0; i < 4; i++) {
      int nr = dims[i][0];
      int nc = dims[i][1];

      Array2DView a1 = new Array2DView(t1, nr, h - nr, nc, w - nc);
      Array2DView a2 = new Array2DView(t2, 0, h - nr, 0, w - nc);

      float peak = crossCorrelation(a1, a2);

      if (Float.isNaN(peak) || Float.isInfinite(peak)) {
        peak = -1.0f;
      }


      corrList.add(new CorrelationTriple(peak, nc, nr));

    }

    for (int i = 0; i < 4; i++) {
      int nr = dims[i][0];
      int nc = dims[i][1];

      Array2DView a1 = new Array2DView(t1, 0, h - nr, nc, w - nc);
      Array2DView a2 = new Array2DView(t2, nr, h - nr, 0, w - nc);

      float peak = crossCorrelation(a1, a2);

      if (Float.isNaN(peak) || Float.isInfinite(peak)) {
        peak = -1.0f;
      }

      corrList.add(new CorrelationTriple(peak, nc, -nr));

    }

    if (corrList.size() == 0)
      return new CorrelationTriple(Float.NEGATIVE_INFINITY, 0, 0);

    return Collections.max(corrList);
  }

  /**
   * Computes cross correlation search with hill climbing (left-right)
   *
   * @param minBoundX min x boundary
   * @param maxBoundX max x boundary
   * @param minBoundY min y boundary
   * @param maxBoundY max y bounadary
   * @param startX    start x position for hill climb
   * @param startY    start y position for hill climb
   * @param i1        the first image for CCF computation (north/west neighbor)
   * @param i2        the second image for CCF computation (current)
   * @return the highest correlation triple within the bounding box using hill climbing
   */
  public static CorrelationTriple computeCCF_HillClimbing_LR(int minBoundX, int maxBoundX,
                                                             int minBoundY, int maxBoundY,
                                                             int startX, int startY,
                                                             ImageTile<?> i1, ImageTile<?> i2) {
    int width = i1.getWidth();
    int height = i1.getHeight();

    int curX = startX;
    int curY = startY;
    float curPeak = Float.NaN;

    minBoundY = Math.max(minBoundY, -height);
    minBoundY = Math.min(minBoundY, height);

    maxBoundY = Math.max(maxBoundY, -height);
    maxBoundY = Math.min(maxBoundY, height);

    minBoundX = Math.max(minBoundX, 0);
    minBoundX = Math.min(minBoundX, width);

    maxBoundX = Math.max(maxBoundX, 0);
    maxBoundX = Math.min(maxBoundX, width);

    // create array of peaks +1 for inclusive, +2 for each end
    int yLength = maxBoundY - minBoundY + 1 + 2;
    int xLength = maxBoundX - minBoundX + 1 + 2;

    float[][] peaks = new float[yLength][xLength];

    boolean foundPeak = false;

    // Compute hill climbing
    while (!foundPeak
        && ((curX <= maxBoundX && curX >= minBoundX) || (curY <= maxBoundY && curY >= minBoundY))) {

      // translate to 0-based index coordinates
      int curYIndex = curY - minBoundY;
      int curXIndex = curX - minBoundX;

      // check current
      if (Float.isNaN(curPeak)) {
        curPeak = getCCFLR(i1, i2, curX, curY, height, width);
        peaks[curYIndex][curXIndex] = curPeak;
      }

      HillClimbDirection direction = HillClimbDirection.NoMove;

      // Check each direction and move based on highest correlation
      for (HillClimbDirection dir : HillClimbDirection.values()) {
        // Skip NoMove direction
        if (dir == HillClimbDirection.NoMove)
          continue;

        float peak = Float.NEGATIVE_INFINITY;

        // Check if moving dir is in bounds
        if (curY + dir.getYDir() >= minBoundY && curY + dir.getYDir() <= maxBoundY
            && curX + dir.getXDir() >= minBoundX && curX + dir.getXDir() <= maxBoundX) {

          // Check if we have already computed the peak at dir
          if (peaks[curYIndex + dir.getYDir()][curXIndex + dir.getXDir()] == 0.0) {
            peak = getCCFLR(i1, i2, curX + dir.getXDir(), curY + dir.getYDir(), height, width);
            peaks[curYIndex + dir.getYDir()][curXIndex + dir.getXDir()] = peak;
          } else {
            peak = peaks[curYIndex + dir.getYDir()][curXIndex + dir.getXDir()];
          }

          // check if dir gives us the best peak
          if (peak > curPeak) {
            curPeak = peak;
            direction = dir;
          }
        }
      }

      // if the direction did not move, then we are done
      if (direction == HillClimbDirection.NoMove) {
        foundPeak = true;
      } else {
        curX += direction.getXDir();
        curY += direction.getYDir();
      }

    }

    if (Float.isInfinite(curPeak)) {
      curX = minBoundX;
      curY = minBoundY;
      curPeak = -1.0f;
    }

    return new CorrelationTriple(curPeak, curX, curY);
  }


  /**
   * Computes cross correlation search with hill climbing (left-right)
   *
   * @param minBoundX min x boundary
   * @param maxBoundX max x boundary
   * @param minBoundY min y boundary
   * @param maxBoundY max y bounadary
   * @param startX    start x position for hill climb
   * @param startY    start y position for hill climb
   * @param i1        the first image for CCF computation (north/west neighbor)
   * @param i2        the second image for CCF computation (current)
   * @return the highest correlation triple within the bounding box using hill climbing
   */
  public static CorrelationTriple computeCCF_Exhaustive_LR(int minBoundX, int maxBoundX,
                                                           int minBoundY, int maxBoundY,
                                                           int startX, int startY,
                                                           ImageTile<?> i1, ImageTile<?> i2) {
    int width = i1.getWidth();
    int height = i1.getHeight();

    int maxX = startX;
    int maxY = startY;
    float curPeak = Float.NaN;
    float maxPeak = Float.NEGATIVE_INFINITY;

    minBoundY = Math.max(minBoundY, -height);
    minBoundY = Math.min(minBoundY, height);

    maxBoundY = Math.max(maxBoundY, -height);
    maxBoundY = Math.min(maxBoundY, height);

    minBoundX = Math.max(minBoundX, 0);
    minBoundX = Math.min(minBoundX, width);

    maxBoundX = Math.max(maxBoundX, 0);
    maxBoundX = Math.min(maxBoundX, width);

    for (int curX = minBoundX; curX <= maxBoundX; curX++) {
      for (int curY = minBoundY; curY <= maxBoundY; curY++) {

        curPeak = getCCFLR(i1, i2, curX, curY, height, width);
        if (curPeak >= maxPeak) {
          maxPeak = curPeak;
          maxX = curX;
          maxY = curY;
        }

      }
    }

    if (Float.isNaN(maxPeak) || Float.isInfinite(curPeak)) {
      maxX = startX;
      maxY = startY;
      maxPeak = -1.0f;
    }

    return new CorrelationTriple(maxPeak, maxX, maxY);
  }


  /**
   * Wrapper that computes the left/right CCF at a given x and y location between two image tile's
   *
   * @param x  the x location
   * @param y  the y location
   * @param i1 the first image tile (north/west neighbor)
   * @param i2 the second image tile (current)
   * @return the correlation triple at x, y
   */
  public static CorrelationTriple computeCCF_LR(int x, int y, ImageTile<?> i1, ImageTile<?> i2) {
    return computeCCF_LR(x, x, y, y, i1, i2);
  }

  /**
   * Computes the left/right CCF values inside a bounding box, returning the best CCF value (one
   * with the highest correlation)
   *
   * @param minBoundX the minimum x value of the bounding box
   * @param maxBoundX the maximum x value of the bounding box
   * @param minBoundY the minimum y value of the bounding box
   * @param maxBoundY the maximum y value of the bounding box
   * @param i1        the first image for CCF computation (north/west neighbor)
   * @param i2        the second image for CCF computation (current)
   * @return the highest correlation triple within the bounding box
   */
  public static CorrelationTriple computeCCF_LR(int minBoundX, int maxBoundX, int minBoundY,
                                                int maxBoundY, ImageTile<?> i1, ImageTile<?> i2) {
    int width = i1.getWidth();
    int height = i1.getHeight();

    float maxPeak = Float.NEGATIVE_INFINITY;
    int x = minBoundX;
    int y = minBoundY;

    for (int j = minBoundX; j <= maxBoundX; j++) {
      for (int i = minBoundY; i <= maxBoundY; i++) {
        Array2DView a1, a2;
        float peak;

        if (i >= 0) {
          a1 = new Array2DView(i1, i, height - i, j, width - j);
          a2 = new Array2DView(i2, 0, height - i, 0, width - j);

        } else {
          a1 = new Array2DView(i1, 0, height + i, j, width - j);
          a2 = new Array2DView(i2, -i, height + i, 0, width - j);
        }

        peak = Stitching32.crossCorrelation(a1, a2);
        if (peak > maxPeak) {
          x = j;
          y = i;
          maxPeak = peak;
        }
      }
    }

    if (Float.isInfinite(maxPeak)) {
      x = minBoundX;
      y = minBoundY;
      maxPeak = -1.0f;
    }

    return new CorrelationTriple(maxPeak, x, y);
  }


  /**
   * Compute the cross correlation function (left-right)
   *
   * @param i1     the first image for CCF computation (north/west neighbor)
   * @param i2     the second image for CCF computation (current)
   * @param x      the x position
   * @param y      the y position
   * @param height the height of the image
   * @param width  the width of the image
   * @return the correlation
   */
  public static float getCCFLR(ImageTile<?> i1, ImageTile<?> i2, int x, int y, int height,
                               int width) {
    Array2DView a1, a2;

    if (x < 0)
      x = 0;

    if (y >= 0) {
      a1 = new Array2DView(i1, y, height - y, x, width - x);
      a2 = new Array2DView(i2, 0, height - y, 0, width - x);

    } else {
      a1 = new Array2DView(i1, 0, height + y, x, width - x);
      a2 = new Array2DView(i2, -y, height + y, 0, width - x);
    }

    return Stitching32.crossCorrelation(a1, a2);
  }

  /**
   * Computes the cross correlation between two arrays
   *
   * @param a1 float array 1
   * @param a2 float array 2
   * @return the cross correlation
   */
  public static float crossCorrelation(Array2DView a1, Array2DView a2) {
    double sum_prod = 0;
    double sum1 = 0;
    double sum2 = 0;
    double norm1 = 0;
    double norm2 = 0;
    double a1_ij;
    double a2_ij;

    int n_rows = a1.getViewHeight();
    int n_cols = a2.getViewWidth();

    int sz = n_rows * n_cols;

    for (int i = 0; i < n_rows; i++)
      for (int j = 0; j < n_cols; j++) {
        a1_ij = a1.getf(i, j);
        a2_ij = a2.getf(i, j);
        sum_prod += a1_ij * a2_ij;
        sum1 += a1_ij;
        sum2 += a2_ij;
        norm1 += a1_ij * a1_ij;
        norm2 += a2_ij * a2_ij;
      }

    double numer = sum_prod - sum1 * sum2 / sz;
    double denom = Math.sqrt((norm1 - sum1 * sum1 / sz) * (norm2 - sum2 * sum2 / sz));

    float val = (float)(numer / denom);

    if (Float.isNaN(val) || Float.isInfinite(val)) {
      val = -1.0f;
    }

    return val;
  }

}
