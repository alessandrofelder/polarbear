/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */

package polarbear;

import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.ops.OpService;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.util.ColorRGB;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static net.imglib2.type.numeric.ARGBType.rgba;

/**
 * This example illustrates how to create an ImageJ {@link Command} plugin.
 * <p>
 * The code here is a simple Gaussian blur using ImageJ Ops.
 * </p>
 * <p>
 * You should replace the parameter fields with your own inputs and outputs,
 * and replace the run method implementation with your own logic.
 * </p>
 */
@Plugin(type = Command.class, menuPath = "Plugins>Polar Bear>Combine Image Stack")
public class CombineNImages<T extends RealType<T>> extends ContextCommand {

    @Parameter
    ImgPlus<T> imp;

    @Parameter
    OpService opService;

    @Parameter
    LogService logService;

    @Parameter(type = ItemIO.OUTPUT)
    private ImgPlus<UnsignedByteType> redChannel;

    @Parameter(type = ItemIO.OUTPUT)
    private ImgPlus<UnsignedByteType> greenChannel;

    @Parameter(type = ItemIO.OUTPUT)
    private ImgPlus<UnsignedByteType> blueChannel;

    //@Parameter(type = ItemIO.OUTPUT)
   // private ImgPlus<ARGBType> merged;


    @Override
    public void run() {


        int nImages = (int) imp.getImg().dimension(2);
        final ArrayList<RandomAccessibleInterval> images = new ArrayList<>();
        for(int i=0; i<nImages; i++)
        {
            images.add(Views.hyperSlice(imp.getImg(),2,i));
        }
        List<ColorRGB> colorList = new ArrayList<>();

        int maxIntensity = 240;
        int[] redWave = new int[nImages];
        int[] greenWave = new int[nImages];
        int[] blueWave = new int[nImages];

        int nZeroes = nImages/3+1;
        int step = maxIntensity/(nImages/3);
        int nSteps = nImages/3-1;
        int peakShift = nImages/3;

        redWave[0]=maxIntensity;
        greenWave[peakShift]=maxIntensity;
        blueWave[2*peakShift]=maxIntensity;

        for(int z=0;z<nZeroes;z++)
        {
            blueWave[z] = 0;
            redWave[z+peakShift]=0;
            greenWave[(z+2*peakShift)%nImages]=0;
        }

        for(int s=0;s<nSteps;s++)
        {
            greenWave[s+1]=(s+1)*step;
            greenWave[(2*peakShift-s-1)%nImages]=(s+1)*step;


            blueWave[s+1+peakShift]=(s+1)*step;
            blueWave[(3*peakShift-s-1)%nImages]=(s+1)*step;

            redWave[(s+1+2*peakShift)%nImages]=(s+1)*step;
            redWave[(4*peakShift-s-1)%nImages]=(s+1)*step;
        }

        double maxGreyScaleValue = 0.0;
        for(int i=0;i<nImages;i++) {
            colorList.add(new ColorRGB(redWave[i], greenWave[i], blueWave[i]));
            final IntervalView<T> ts = Views.hyperSlice(imp.getImg(), 2, i);
            double sliceMaximum = opService.stats().max(ts).getRealDouble();
            if(sliceMaximum>maxGreyScaleValue) maxGreyScaleValue=sliceMaximum;
        }
        double weight = 1.0/nImages;
        if(maxGreyScaleValue>255.0) {
            logService.info("Max stack grey scale value is "+maxGreyScaleValue+". Assuming 16-bit image.");
            weight = weight / Math.pow(2, 8);
        }
        else{
            logService.info("Max stack grey scale value is "+maxGreyScaleValue+"<255. Assuming 8-bit image.");
        }

        Img<DoubleType> interpolatedRed = ArrayImgs.doubles(images.get(0).dimension(0), images.get(0).dimension(1), 1);
        Img<DoubleType> interpolatedGreen = ArrayImgs.doubles(images.get(0).dimension(0), images.get(0).dimension(1), 1);
        Img<DoubleType> interpolatedBlue = ArrayImgs.doubles(images.get(0).dimension(0), images.get(0).dimension(1), 1);

        for(int k = 0; k<nImages; k++)
        {
            final RandomAccessibleInterval<T> slice = images.get(k);
            final ColorRGB rgb = colorList.get(k);
            final RandomAccess<T> randomAccess = slice.randomAccess();
            for(int l = 0; l<slice.max(0)+1; l++) {
                for(int m = 0; m<slice.max(1)+1; m++){
                    long[] position = new long[]{l,m,k};
                    randomAccess.setPosition(position);
                    double localValue = randomAccess.get().getRealDouble()/(Math.pow(2,8)-1);
                    position[2]=0;
                    addValueAtPosition(interpolatedRed, weight*rgb.getRed()*localValue,position);
                    addValueAtPosition(interpolatedGreen, weight*rgb.getGreen()*localValue,position);
                    addValueAtPosition(interpolatedBlue, weight*rgb.getBlue()*localValue,position);

                }
            }
        }

        final Img<UnsignedByteType> red = opService.convert().uint8(interpolatedRed);
        redChannel = new ImgPlus<>(red, "red");
        final Img<UnsignedByteType> green = opService.convert().uint8(interpolatedGreen);
        greenChannel = new ImgPlus<>(green, "green");
        final Img<UnsignedByteType> blue = opService.convert().uint8(interpolatedBlue);
        blueChannel = new ImgPlus<>(blue, "blue");

        /*final Img<ARGBType> rgbImage = opService.create().img(red, new ARGBType());
        final Cursor<ARGBType> rgbCursor = rgbImage.localizingCursor();
        while(rgbCursor.hasNext()){
            rgbCursor.fwd();
            long[] position = new long[3];
            rgbCursor.localize(position);

            RandomAccess<UnsignedByteType> redRA = red.randomAccess();
            redRA.setPosition(position);

            RandomAccess<UnsignedByteType> greenRA = green.randomAccess();
            greenRA.setPosition(position);

            RandomAccess<UnsignedByteType> blueRA = blue.randomAccess();
            blueRA.setPosition(position);

            final int rgba = rgba(redRA.get().get(), greenRA.get().get(), blueRA.get().get(),1);
            rgbCursor.get().set(rgba);
        }

        merged = new ImgPlus<>(rgbImage,"Merged");*/

    }

    /**
     * This main function serves for development purposes.
     * It allows you to run the plugin immediately out of
     * your integrated development environment (IDE).
     *
     * @param args whatever, it's ignored
     * @throws Exception
     */
    public static void main(final String... args) throws Exception {
        // create the ImageJ application context with all available services
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();

        // ask the user for a file to open
        final File file = ij.ui().chooseFile(null, "open");

        if (file != null) {
            // load the dataset
            final Dataset dataset = ij.scifio().datasetIO().open(file.getPath());

            // show the image
            ij.ui().show(dataset);

            // invoke the plugin
            ij.command().run(CombineNImages.class, true);
        }
    }

    private void addValueAtPosition(Img<DoubleType> image, double valueToAdd, long[] position) {
        final RandomAccess<DoubleType> access = image.randomAccess();
        access.setPosition(position);
        double currentValue = access.get().getRealDouble();
        access.get().setReal(currentValue+valueToAdd);
    }

}
