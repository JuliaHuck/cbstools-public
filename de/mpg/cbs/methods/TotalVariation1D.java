package de.mpg.cbs.methods;

import de.mpg.cbs.utilities.*;
import org.apache.commons.math3.util.FastMath;

/**
 *
 *  This algorithm computes basic total variation minimizers
 *  from Chambolle's algorithm
 *
 *	@version    July 2014
 *	@author     Pierre-Louis Bazin
 *		
 *
 */
 
public class TotalVariation1D {
		
	// numerical quantities
	private static final	float   INF=1e30f;
	private static final	float   ZERO=1e-30f;
	
	// data buffers
	private 	float[]			image;  			// original image
	private 	float[][]		proj;				// tv-projection function
	private 	boolean[]		mask;   			// image mask: true for data points
	private		static	int			nx,ny,nz,nxyz;   		// image dimensions
	private		float 				Imin, Imax;
	private		float				Isize;				// non-masked region size
	
	// parameters
	private 	float 		lambdaScale = 0.05f;		// scaling parameter
	private 	float 		tauStep = 0.125f;		// internal step parameter (default 1/4)
	private 	float 		maxdist = 0.0001f;		// maximum error for stopping
	private 	int 		maxiter = 100;		// maximum number of iterations
			
	// computation variables
	private 	float[]			field;  // image difference field
	
	private static final byte X = 0;
	private static final byte Y = 1;
	private static final byte Z = 2;
	
	// for debug
	static final boolean		debug=true;
	static final boolean		verbose=true;
	
	/**
	 *  constructor
	 *	note: all images passed to the algorithm are just linked, not copied
	 */
	 
	public TotalVariation1D(float[] image_, boolean [] mask_, 
					int nx_, int ny_, int nz_,
					float scale_, float step_, float dist_, int iter_) {
		
		image = image_;
		mask = mask_;

		nx = nx_;
		ny = ny_;
		nz = nz_;
		nxyz = nx*ny*nz;
		
		if (mask==null) {
		    mask = new boolean[nxyz];
		    for (int xyz=0;xyz<nxyz;xyz++) mask[xyz] = true;
		}
		
		lambdaScale = scale_;
		tauStep = step_;
		maxdist = dist_;
		maxiter = iter_;

		// init all the new arrays
		try {
			proj = new float[3][nxyz];
			field = new float[nxyz];
		} catch (OutOfMemoryError e){
			finalize();
			System.out.println(e.getMessage());
			return;
		}
		
		// init values
		Imin = INF;
		Imax = -INF;
		Isize = 0.0f;
		for (int xyz=0;xyz<nxyz;xyz++) {
			if (image[xyz]<Imin) Imin = image[xyz];
			if (image[xyz]>Imax) Imax = image[xyz];

			proj[X][xyz] = 0.0f;
			proj[Y][xyz] = 0.0f;
			proj[Z][xyz] = 0.0f;
			
			if (mask[xyz]) Isize++;
		}
		// rescale intensities in [0,1]: no!
		//for (int xyz=0;xyz<nxyz;xyz++) {
		//	image[xyz] = (image[x][y][z]-Imin)/(Imax-Imin);
		//}
		// scale parameter: invariant by intensity scaling
		//lambdaScale *= (Imax-Imin)*(Imax-Imin);
		
		if (debug) BasicInfo.displayMessage("TV:initialisation\n");
	}

	/** clean-up: destroy membership and centroid arrays */
	public final void finalize() {
		proj = null;
		field = null;
		System.gc();
	}
	
    /** accessor for computed data */ 
    public final float[][] getProjection() { return proj; }
    
    /** 
	 *  compute the new TV projection function
	 */
    final public double computeProjection() {
        double distance, dist;
        double norm, divp;
        double[] grad = new double[3];
        double[] prev = new double[3];
        
		distance = 0.0;
		// we ignore the image boundary for convenience
		for (int x=0;x<nx;x++) for (int y=0;y<ny;y++) for (int z=0;z<nz;z++) {
		    int xyz = x+nx*y+nx*ny*z;
		    if (mask[xyz]) {
		        divp = 0.0;
		        // first compute the intermediate field
                if (x-1>=0) divp += (proj[X][xyz]-proj[X][xyz-1]);
                if (y-1>=0) divp += (proj[Y][xyz]-proj[Y][xyz-nx]);
				if (z-1>=0) divp += (proj[Z][xyz]-proj[Z][xyz-nx*ny]);
			
                field[xyz] = (float)(divp - (image[xyz]-Imin)/(Imax-Imin)/lambdaScale);
            }
		}
		for (int x=0;x<nx;x++) for (int y=0;y<ny;y++) for (int z=0;z<nz;z++) {
		    int xyz = x+nx*y+nx*ny*z;
		    if (mask[xyz]) {
		        // second compute the projection update
                prev[X] = proj[X][xyz];
                prev[Y] = proj[Y][xyz];
                prev[Z] = proj[Z][xyz];
                
                if (x+1<nx) grad[X] = field[xyz+1]-field[xyz];
                else grad[X] = 0.0;
                if (y+1<ny) grad[Y] = field[xyz+nx]-field[xyz];
                else grad[Y] = 0.0;
                if (z+1<nz) grad[Z] = field[xyz+nx*ny]-field[xyz];
                else grad[Z] = 0.0;
                norm = (1.0 + tauStep*FastMath.sqrt(grad[X]*grad[X]+grad[Y]*grad[Y]+grad[Z]*grad[Z]));
			
                proj[X][xyz] = (float)( (prev[X] + tauStep*grad[X])/norm);
                proj[Y][xyz] = (float)( (prev[Y] + tauStep*grad[Y])/norm);
                proj[Z][xyz] = (float)( (prev[Z] + tauStep*grad[Z])/norm);
			
                dist = 	 (proj[X][xyz]-prev[X])*(proj[X][xyz]-prev[X])
                        +(proj[Y][xyz]-prev[Y])*(proj[Y][xyz]-prev[Y])
                        +(proj[Z][xyz]-prev[Z])*(proj[Z][xyz]-prev[Z]);
					
                if (dist>distance) distance = dist;
            }
		}

        return distance;
    } // computeMemberships
    
    /** 
	 *  compute the new TV projection function
	 */
    final public double computeProjectionWrapped() {
        double distance, dist;
        double norm, divp;
        double[] grad = new double[3];
        double[] prev = new double[3];
        
		distance = 0.0;
		// we ignore the image boundary for convenience
		for (int x=0;x<nx;x++) for (int y=0;y<ny;y++) for (int z=0;z<nz;z++) {
		    int xyz = x+nx*y+nx*ny*z;
		    if (mask[xyz]) {
		        divp = 0.0;
		        // first compute the intermediate field
                if (x-1>=0) divp += Numerics.modulo(proj[X][xyz]-proj[X][xyz-1], Imax-Imin);
                if (y-1>=0) divp += Numerics.modulo(proj[Y][xyz]-proj[Y][xyz-nx], Imax-Imin);
				if (z-1>=0) divp += Numerics.modulo(proj[Z][xyz]-proj[Z][xyz-nx*ny], Imax-Imin);
			
				//  wrapping only for spatial derivatives
                field[xyz] = (float)(divp - (image[xyz]-Imin)/(Imax-Imin)/lambdaScale);
                //field[xyz] = (float)Numerics.modulo(divp - (image[xyz]-Imin)/(Imax-Imin)/lambdaScale, Imax-Imin);
            }
		}
		for (int x=0;x<nx;x++) for (int y=0;y<ny;y++) for (int z=0;z<nz;z++) {
		    int xyz = x+nx*y+nx*ny*z;
		    if (mask[xyz]) {
		        // second compute the projection update
                prev[X] = proj[X][xyz];
                prev[Y] = proj[Y][xyz];
                prev[Z] = proj[Z][xyz];
                
                if (x+1<nx) grad[X] = Numerics.modulo(field[xyz+1]-field[xyz], Imax-Imin);
                else grad[X] = 0.0;
                if (y+1<ny) grad[Y] = Numerics.modulo(field[xyz+nx]-field[xyz], Imax-Imin);
                else grad[Y] = 0.0;
                if (z+1<nz) grad[Z] = Numerics.modulo(field[xyz+nx*ny]-field[xyz], Imax-Imin);
                else grad[Z] = 0.0;
                norm = (1.0 + tauStep*FastMath.sqrt(grad[X]*grad[X]+grad[Y]*grad[Y]+grad[Z]*grad[Z]));
			
                proj[X][xyz] = (float)( (prev[X] + tauStep*grad[X])/norm);
                proj[Y][xyz] = (float)( (prev[Y] + tauStep*grad[Y])/norm);
                proj[Z][xyz] = (float)( (prev[Z] + tauStep*grad[Z])/norm);
			
                dist = 	 (proj[X][xyz]-prev[X])*(proj[X][xyz]-prev[X])
                        +(proj[Y][xyz]-prev[Y])*(proj[Y][xyz]-prev[Y])
                        +(proj[Z][xyz]-prev[Z])*(proj[Z][xyz]-prev[Z]);
					
                if (dist>distance) distance = dist;
            }
		}

        return distance;
    } // computeMemberships
    
    /**
	 * denoising algorithm
	 */
    final public void solve() {
    	double fn;
    	double size = nx*ny*nz;
    	double ratio = 1.0;
    	
    	// loop until distance is minimized
    	double distance = 1e10;
    	int t=0;
    	while ((distance>maxdist || t<0) && t<maxiter) {
    		t++;
    		if (verbose) System.out.print("iter "+t);
			// get the new projection
			distance = computeProjection();
			
			if (verbose) System.out.println(": d="+distance);
        }
        return;
    }
        
    /**
	 * denoising algorithm
	 */
    final public void solveWrapped() {
    	double fn;
    	double size = nx*ny*nz;
    	double ratio = 1.0;
    	
    	// loop until distance is minimized
    	double distance = 1e10;
    	int t=0;
    	while ((distance>maxdist || t<0) && t<maxiter) {
    		t++;
    		if (verbose) System.out.print("iter "+t);
			// get the new projection
			distance = computeProjectionWrapped();
			
			if (verbose) System.out.println(": d="+distance);
        }
        return;
    }
        
    /**
	 * denoising algorithm
	 */
    final public void denoiseImage(float stdev, boolean adaptive) {
    	double fn;
    	double ratio = 1.0;
    	
    	// loop until distance is minimized
    	double distance = 1e10;
    	int t=0;
    	double sqdist = FastMath.sqrt(maxdist);
    	while ((distance>maxdist || t<0) && t<maxiter) {
    		t++;
    		if (verbose) System.out.print("iter "+t);
			// get the new projection
			distance = computeProjection();
			
			// get the scaling factor
			fn = 0.0;
			for (int x=0;x<nx;x++) for (int y=0;y<ny;y++) for (int z=0;z<nz;z++) {
                int xyz = x+nx*y+nx*ny*z;
                if (mask[xyz]) {
                    double divp = 0.0;
                    // first compute the intermediate field
                    if (x-1>=0) divp += (proj[X][xyz]-proj[X][xyz-1]);
                    if (y-1>=0) divp += (proj[Y][xyz]-proj[Y][xyz-nx]);
                    if (z-1>=0) divp += (proj[Z][xyz]-proj[Z][xyz-nx*ny]);
                
                    fn += divp*divp;
                }
            }
			fn = FastMath.sqrt(fn/Isize)*lambdaScale;
			//fn *= lambdaScale/Isize;
			ratio = (stdev/fn);
	
			// change scaling only after some level of convergence
			if (adaptive && distance<sqdist) {
				// update the lambda
				lambdaScale *= ratio;
			}
			if (verbose) System.out.println(": d="+distance+", f="+fn+", r="+ratio);
        }
        return;
    }
        
	/** 
	 *	export membership functions 
	 */
	public final float[] exportResult() {
		float[]	res = new float[nxyz];
		
        for (int x=0;x<nx;x++) for (int y=0;y<ny;y++) for (int z=0;z<nz;z++) {
            int xyz = x+nx*y+nx*ny*z;
            if (mask[xyz]) {
                double divp = 0.0;
                // first compute the intermediate field
                if (x-1>=0) divp += (proj[X][xyz]-proj[X][xyz-1]);
                if (y-1>=0) divp += (proj[Y][xyz]-proj[Y][xyz-nx]);
                if (z-1>=0) divp += (proj[Z][xyz]-proj[Z][xyz-nx*ny]);
            
                res[xyz] = (float)((image[xyz]-Imin)/(Imax-Imin) - lambdaScale*divp);
            }
        }
		return res;
	} // exportMemberships
	
	/** 
	 *	export membership functions 
	 */
	public final float[] exportResultWrapped() {
		float[]	res = new float[nxyz];
		
        for (int x=0;x<nx;x++) for (int y=0;y<ny;y++) for (int z=0;z<nz;z++) {
            int xyz = x+nx*y+nx*ny*z;
            if (mask[xyz]) {
                double divp = 0.0;
                // first compute the intermediate field
                if (x-1>=0) divp += Numerics.modulo(proj[X][xyz]-proj[X][xyz-1], Imax-Imin);
                if (y-1>=0) divp += Numerics.modulo(proj[Y][xyz]-proj[Y][xyz-nx], Imax-Imin);
                if (z-1>=0) divp += Numerics.modulo(proj[Z][xyz]-proj[Z][xyz-nx*ny], Imax-Imin);
            
                // only wrapping the spatial derivatives
                res[xyz] = (float)((image[xyz]-Imin)/(Imax-Imin) - lambdaScale*divp);
                //res[xyz] = (float)Numerics.modulo((image[xyz]-Imin)/(Imax-Imin) - lambdaScale*divp, Imax-Imin);
            }
        }
		return res;
	} // exportMemberships

}