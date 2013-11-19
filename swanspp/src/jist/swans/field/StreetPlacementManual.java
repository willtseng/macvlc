package jist.swans.field;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import jist.swans.Constants;
import jist.swans.field.StreetMobilityOD.StreetMobilityInfoOD;
import jist.swans.field.StreetMobilityRandom.StreetMobilityInfoRandom;
import jist.swans.field.streets.RoadSegment;
import jist.swans.field.streets.SegmentNode;
import jist.swans.field.streets.Shape;
import jist.swans.misc.Location;

/**
 * @author Ted Ekeroth, Michael Rozenberg. NCSU February 2006
 * Modified version of StreetPlacementRandom.java
 * 
 * The StreetPlacementManual class places nodes manually. Each nodes needs a segmendId to be placed.
 * This should only be used for testing purposes.
 */
public class StreetPlacementManual implements Placement {

    /** placement boundaries. */
    private float xBL, yBL, xTR, yTR;
    /** street mobility object, stores roads */
    private StreetMobility sm; 
    /** set true for debugging information */
    final private boolean DEBUG=false;
    /** incremented each time this is called to determine node number */
    private int numberOfNodes=0;
    /** the random object for repeatability */
    private java.util.Random rnd;
    /** the maximum distance in meters between first point and destination */
    float threshold = 1500.0f;
    /** the std dev for the distribution of speeds about the speed limit. */
    private double stdDev = 0.0;
    /** step time for street mobility */
    private long stepTime= Constants.SECOND;
	
	private BufferedReader bk;
	
	public StreetPlacementManual(StreetMobility smr)
	{
		sm = smr;
		bk = new BufferedReader (new InputStreamReader(System.in));
		this.rnd = smr.rnd;
	}
	
	public Location getNextLocation() 
	{
		Location initialLocation;
        RoadSegment rs = null;
        int segmentID=0,direction=0, position = 0;
        boolean full=false;
        boolean valid = false; // did this find a valid street pair?
        float remainingDist;
        final int TO_START = 0;
        final int TO_END = 1;

        
//      determine mobility model
        StreetMobilityInfo smri;
        if (sm instanceof StreetMobilityRandom)
        {
            smri = new StreetMobilityInfoRandom();
        }
        else
        {
            smri = new StreetMobilityInfoOD();
        }
        
		System.out.println ("*** Placing a car. ***");
		System.out.print("Enter origin segmentID: ");

	    String sb = "0";
		try {
			sb = bk.readLine();
		} catch (IOException e) {
			e.printStackTrace();
		}
	    segmentID = Integer.parseInt(sb); 

	    direction = Constants.random.nextInt(2); // pick direction
        rs = (RoadSegment)sm.segments.elementAt(segmentID);

        // make sure lane isn't full
        if (direction==TO_START)
        {
            if ((rs.getCarsToStart().size()+1)>rs.getMaxCars()) full=true;
            else full=false;
        }
        else
        {
            if ((rs.getCarsToEnd().size()+1)>rs.getMaxCars()) full=true;
            else full=false;
        }               
		
//      set the rsEnd for calculating path
        if (direction == TO_START) smri.rsEnd = rs.getStartPoint();
        else smri.rsEnd = rs.getEndPoint();
        
        // in the case of the SMOD model, some values have to be initialized
        if (sm instanceof StreetMobilityOD)
        {
            StreetMobilityOD smod = (StreetMobilityOD)sm;
            smri.current = rs;
            
            /// manual dest road segment            
            RoadSegment dest = null;
            while (dest == null || dest.getSelfIndex() == rs.getSelfIndex() || dest.getEndPoint().distance(rs.getEndPoint())>threshold) 
            {
            	System.out.println ("*** Placing a car. ***");
            	//if (dest != null)
            		//System.out.println(dest.getSelfIndex()+" "+rs.getSelfIndex()+" "+dest.getEndPoint().distance(rs.getEndPoint()));
        		System.out.print("Enter destination segmentID: ");

        	    String sb_dest = "0";
        		try {
        			sb_dest = bk.readLine();
        		} catch (IOException e) {
        			e.printStackTrace();
        		}
        	    int segmentID_dest = Integer.parseInt(sb_dest); 
            	
                // this should pick a relatively local location
                dest = (RoadSegment)smod.segments.get(segmentID_dest);
            }
            
            // calculate OD path
            valid = smod.calculatePath((StreetMobilityInfoOD)smri, rs, smri.rsEnd, dest);
            
            // only valid path may force car to be in other lane
            if (valid)
            {
                StreetMobilityInfoOD smiod=(StreetMobilityInfoOD)smri;
                SegmentNode sn = (SegmentNode)smiod.path.getFirst();
                RoadSegment rsTemp = (RoadSegment)sm.segments.get(sn.segmentID);
                
                // needs to be in opposite lane
                if (sn.segmentID == rs.getSelfIndex() && 
                        sn.point.distance(smri.rsEnd)<StreetMobility.INTERSECTION_RESOLUTION )
                         
                {
                    if (smri.rsEnd.distance(rs.getEndPoint())==0)
                    {
                        smri.rsEnd = rs.getEndPoint();
                        direction = TO_END;
                    }
                    else
                    {
                        smri.rsEnd = rs.getStartPoint();
                        direction = TO_START;
                    }
                    smiod.path.remove(0);
                }
                
                // another case where it needs to be in opposite lane
                // diagnosis: rsEnd is not an endpoint in the next segment
                // needs to be in opposite lane
                if (rsTemp.getEndPoint().distance(smri.rsEnd)>StreetMobility.INTERSECTION_RESOLUTION
                        && rsTemp.getStartPoint().distance(smri.rsEnd)>StreetMobility.INTERSECTION_RESOLUTION)
                {
                    if (smri.rsEnd.distance(rs.getEndPoint())==0)
                    {
                        smri.rsEnd = rs.getStartPoint();
                        direction = TO_START;
                        if (rs.getCarsToStart().size()>=rs.getMaxCars()) full=true;
                    }
                    else
                    {
                        smri.rsEnd = rs.getEndPoint();
                        direction = TO_END;
                        if (rs.getCarsToEnd().size()>=rs.getMaxCars()) full=true;
                    }
                    
                    if (full){
                        valid = false;
                        //continue;
                    }
                    if (DEBUG)System.out.println("Put node in opposite lane!");
                }
            }
        }
        else // not using OD pairs
        {
            valid = true;
        }
		
        //........
        
        numberOfNodes++; // increment number of nodes           
        // update street mobility state
        smri.current = rs;
        smri.currSpeed=0;
        smri.setMaxSpeed(rs.getSpeedLimit());
        smri.stepTime = stepTime;
        
        if (direction==TO_START)
        {            
            if (rs.getShapeIndex()>=0)
            {
                smri.ShapePointIndex = ((Shape)sm.shapes.get(new Integer(rs.getShapeIndex()))).points.length;
            }            
            // for calculating remaining distance
            position = rs.getCarsToStart().size();
        }
        else
        {
            smri.ShapePointIndex = -1;            
            // for calculating remaining distance
            position = rs.getCarsToEnd().size();
        }
        
        remainingDist = rs.getCarSpacing(smri.currSpeed, smri.spacingBeta, 
                smri.spacingGamma)*position;
        smri.remainingDist = remainingDist;
      
        smri.nextRS = rs;
        // add car to road
        // Ted: info till mobInfo l�ggs till h�r kanske?
        smri.currentLane = rs.addNode(smri, smri.rsEnd, sm.mobInfo);
        if( smri.currentLane == null )
        {
            if (direction == TO_START)
            {
                System.out.println("Cars in lane: " + rs.getCarsToStart().size());
                System.out.println("Max cars in lane: " + rs.getMaxCars());
            }
            else
            {
                System.out.println("Cars in lane: " + rs.getCarsToEnd().size());
                System.out.println("Max cars in lane: " + rs.getMaxCars());
            }
            throw new RuntimeException("Not enough room for car!");
        }
        
        sm.setNextRoad(smri);
        // set next car
        if (smri.currentLane.size()>1)
        {
            smri.nextCar = (StreetMobilityInfo)smri.currentLane.get(smri.currentLane.size()-2);
        }
        
        // set the amount by which drivers' speeds will vary from the speed limit
        smri.extraSpeed = (float)(stdDev*rnd.nextGaussian());
        
        // add car to mobility info object
        sm.mobInfo.add(smri); // TODO this is NOT thread safe       
        
        // now we have to move the vehicle back from the end of the road if 
        // there is one or more cars ahead
        if (direction==TO_START)
        {
            // we have no intermediate segments
            if (smri.current.getShapeIndex()==-1)
            {
                initialLocation = sm.move(rs.getEndPoint(), smri.rsEnd, rs.getLength()-remainingDist);
            }
            else
            {
                initialLocation = sm.pointAt(rs.getEndPoint(), smri, 
                        rs.getLength()-remainingDist);
            }
        }
        else
        {
            // we have no intermediate segments
            if (smri.current.getShapeIndex()==-1)
            {
                initialLocation = sm.move(rs.getStartPoint(), smri.rsEnd, rs.getLength()-remainingDist);
            }
            else
            {
                initialLocation = sm.pointAt(rs.getStartPoint(), smri, rs.getLength()-remainingDist); 
            }
        }
        
        System.out.println("Nbr of nodes placed: "+sm.mobInfo.size());

        if (StreetMobility.ENABLE_LANE_DISPLACEMENT){
            smri.offset = sm.getLaneDisplacement(smri, null);
            initialLocation = initialLocation.getClone();
            initialLocation.add(smri.offset); // displace due to lane
        }
        
        return initialLocation;
		
	}
	

}