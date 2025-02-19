/*
 * Copyright (C) 2013 Jeremy Gooch <http://www.linkedin.com/in/jeremygooch/>
 *
 * The licence covering the contents of this file is described in the file LICENCE.txt,
 * which should have been included as part of the distribution containing this file.
 */
package org.goochjs.glicko2;

import java.util.List;

/**
 * This is the main calculation engine based on the contents of Glickman's paper.
 * http://www.glicko.net/glicko/glicko2.pdf
 * 
 * @author Jeremy Gooch
 * 
 */
public class RatingCalculator {

	private final static double DEFAULT_RATING =  1500.0;
	private final static double DEFAULT_DEVIATION =  350;
	private final static double DEFAULT_VOLATILITY =  0.06;
	/** Reasonable choices of TAU are between 0.3 and 1.2,
	 though the system should be tested to decide which value results in greatest predictive
	 accuracy. Smaller values of τ prevent the volatility measures from changing by large amounts,
	 which in turn prevent enormous changes in ratings based on very improbable results. */
	private final static double DEFAULT_TAU =  0.75;
	private final static double MULTIPLIER =  173.7178; /** for converting the ratings and RD’s onto the Glicko-2 scale */
	private final static double CONVERGENCE_TOLERANCE =  0.000001;
	
	private double tau; // constrains volatility over time
	private double defaultVolatility;
	
	
	/**
	 * Standard constructor, taking default values for volatility
	 */
	public RatingCalculator() {
		tau = DEFAULT_TAU;
		defaultVolatility = DEFAULT_VOLATILITY;
	}

	
	/**
	 * 
	 * @param initVolatility  Initial volatility for new ratings
	 * @param tau             How volatility changes over time
	 */
	public RatingCalculator(
			double initVolatility,
			double tau) {
		
		this.defaultVolatility = initVolatility;
		this.tau = tau;
	}

	
	/**
	 * <p>Run through all players within a resultset and calculate their new ratings.</p>
	 * <p>Players within the resultset who did not compete during the rating period
	 * will have see their deviation increase (in line with Prof Glickman's paper).</p>
	 * <p>Note that this method will clear the results held in the association resultset.</p>
	 * 
	 * @param results
	 */
	public void updateRatings(RatingPeriodResults results) {
		for ( Rating player : results.getParticipants() ) {
			if ( results.getResults(player).size() > 0 ) {
				calculateNewRating(player, results.getResults(player));
			} else {
				// if a player does not compete during the rating period, then only Step 6 applies.
				// the player's rating and volatility parameters remain the same but deviation increases
				player.setWorkingRating(player.getGlicko2Rating());
				player.setWorkingRatingDeviation(calculateNewRD(player.getGlicko2RatingDeviation(), player.getVolatility()));
				player.setWorkingVolatility(player.getVolatility());
			}
		}
		
		// now iterate through the participants and confirm their new ratings
		for ( Rating player : results.getParticipants() ) {
			player.finaliseRating();
		}
		
		// lastly, clear the result set down in anticipation of the next rating period
		results.clear();
	}

	
	/**
	 * This is the function processing described in step 5 of Glickman's paper.
	 *  
	 * @param player
	 * @param results
	 */
	private void calculateNewRating(Rating player, List<Result> results) {
		double phi = player.getGlicko2RatingDeviation();
		double sigma = player.getVolatility();
		double a = Math.log( Math.pow(sigma, 2) );
		double delta = delta(player, results);
		double v = v(player, results);
		
		// step 5.2 - set the initial values of the iterative algorithm to come in step 5.4
		double A = a;
		double B = 0.0;
 		if ( Math.pow(delta, 2) > Math.pow(phi, 2) + v ) {
			B = Math.log( Math.pow(delta, 2) - Math.pow(phi, 2) - v );			
		} else {
			double k = 1;
			B = a - ( k * Math.abs(tau));
			
			while ( f(B , delta, phi, v, a, tau) < 0 ) {
				k++;
				B = a - ( k * Math.abs(tau));
			}
		}

		// step 5.3
		double fA = f(A , delta, phi, v, a, tau);
		double fB = f(B , delta, phi, v, a, tau);

		// step 5.4
		while ( Math.abs(B - A) > CONVERGENCE_TOLERANCE ) {
 			double C = A + (( (A-B)*fA ) / (fB - fA));
 			double fC = f(C , delta, phi, v, a, tau);
 			
 			if ( fC * fB < 0 ) {
 				A = B;
 				fA = fB;
 			} else {	
 				fA = fA / 2.0;
 			}
 			
 			B = C;
 			fB = fC;
 		}
 		
		double newSigma = Math.exp( A/2.0 );
 		
		player.setWorkingVolatility(newSigma);

		// Step 6
		double phiStar = calculateNewRD( phi, newSigma );
		
		// Step 7
		double newPhi = 1.0 / Math.sqrt(( 1.0 / Math.pow(phiStar, 2) ) + ( 1.0 / v ));

		// note that the newly calculated rating values are stored in a "working" area in the Rating object
		// this avoids us attempting to calculate subsequent participants' ratings against a moving target
		player.setWorkingRating(
				player.getGlicko2Rating()
				+ ( Math.pow(newPhi, 2) * outcomeBasedRating(player, results)));
		player.setWorkingRatingDeviation(newPhi);
		player.incrementNumberOfResults(results.size());
	}
	
	private double f(double x, double delta, double phi, double v, double a, double tau) {
		return ( Math.exp(x) * ( Math.pow(delta, 2) - Math.pow(phi, 2) - v - Math.exp(x) ) /
				(2.0 * Math.pow( Math.pow(phi, 2) + v + Math.exp(x), 2) )) - 
				( ( x - a ) / Math.pow(tau, 2) );
	}
	
	
	/**
	 * This is the first sub-function of step 3 of Glickman's paper.
	 * 
	 * @param deviation
	 * @return
	 */
	private double g(double deviation) {
		return 1.0 / ( Math.sqrt( 1.0 + ( 3.0 * Math.pow(deviation, 2) / Math.pow(Math.PI,2) )));
	}
	
	
	/**
	 * This is the second sub-function of step 3 of Glickman's paper.
	 * 
	 * @param playerRating
	 * @param opponentRating
	 * @param opponentDeviation
	 * @return
	 */
	private double E(double playerRating, double opponentRating, double opponentDeviation) {
		return 1.0 / (1.0 + Math.exp( -1.0 * g(opponentDeviation) * ( playerRating - opponentRating )));
	}
	
	
	/**
	 * This is the main function in step 3 of Glickman's paper.
	 * 
	 * @param player
	 * @param results
	 * @return
	 */
	private double v(Rating player, List<Result> results) {
		double v = 0.0;
		
		for ( Result result: results ) {
			v = v + (
					( Math.pow( g(result.getOpponent(player).getGlicko2RatingDeviation()), 2) )
					* E(player.getGlicko2Rating(),
							result.getOpponent(player).getGlicko2Rating(),
							result.getOpponent(player).getGlicko2RatingDeviation())
					* ( 1.0 - E(player.getGlicko2Rating(),
							result.getOpponent(player).getGlicko2Rating(),
							result.getOpponent(player).getGlicko2RatingDeviation())
					));
		}
		
		return Math.pow(v, -1);
	}
	
	
	/**
	 * This is a formula as per step 4 of Glickman's paper.
	 * 
	 * @param player
	 * @param results
	 * @return delta
	 */
	private double delta(Rating player, List<Result> results) {
		return v(player, results) * outcomeBasedRating(player, results);
	}
	
	
	/**
	 * This is a formula as per step 4 of Glickman's paper.
	 * 
	 * @param player
	 * @param results
	 * @return expected rating based on game outcomes
	 */
	private double outcomeBasedRating(Rating player, List<Result> results) {
		double outcomeBasedRating = 0;
		
		for ( Result result: results ) {
			outcomeBasedRating = outcomeBasedRating
					+ ( g(result.getOpponent(player).getGlicko2RatingDeviation())
						* ( result.getScore(player) - E(
								player.getGlicko2Rating(),
								result.getOpponent(player).getGlicko2Rating(),
								result.getOpponent(player).getGlicko2RatingDeviation() ))
				);
		}
		
		return outcomeBasedRating;
	}
	
	
	/**
	 * This is the formula defined in step 6. It is also used for players
	 * who have not competed during the rating period.
	 * 
	 * @param phi
	 * @param sigma
	 * @return new rating deviation
	 */
	private double calculateNewRD(double phi, double sigma) {
		return Math.sqrt( Math.pow(phi, 2) + Math.pow(sigma, 2) );
	}

	
	/**
	 * Converts from the value used within the algorithm to a rating in the same range as traditional Elo et al
	 * 
	 * @param rating in Glicko2 scale
	 * @return rating in Glicko scale
	 */
	public static double convertRatingToOriginalGlickoScale(double rating) {
		return ( ( rating  * MULTIPLIER ) + DEFAULT_RATING );
	}
	
	
	/**
	 * Converts from a rating in the same range as traditional Elo et al to the value used within the algorithm
	 * 
	 * @param rating in Glicko scale
	 * @return rating in Glicko2 scale
	 */
	public static double convertRatingToGlicko2Scale(double rating) {
		return ( ( rating  - DEFAULT_RATING ) / MULTIPLIER ) ;
	}
	
	
	/**
	 * Converts from the value used within the algorithm to a rating deviation in the same range as traditional Elo et al
	 * 
	 * @param ratingDeviation in Glicko2 scale
	 * @return ratingDeviation in Glicko scale
	 */
	public static double convertRatingDeviationToOriginalGlickoScale(double ratingDeviation) {
		return ( ratingDeviation * MULTIPLIER ) ;
	}
	
	
	/**
	 * Converts from a rating deviation in the same range as traditional Elo et al to the value used within the algorithm
	 * 
	 * @param ratingDeviation in Glicko scale
	 * @return ratingDeviation in Glicko2 scale
	 */
	public static double convertRatingDeviationToGlicko2Scale(double ratingDeviation) { 
		return ( ratingDeviation / MULTIPLIER );
	}

	
	public double getDefaultRating() {
		return DEFAULT_RATING;
	}

	
	public double getDefaultVolatility() {
		return defaultVolatility;
	}

	
	public double getDefaultRatingDeviation() {
		return DEFAULT_DEVIATION;
	}
}



