/*
 * Copyright (C) 2013 Jeremy Gooch <http://www.linkedin.com/in/jeremygooch/>
 *
 * The licence covering the contents of this file is described in the file LICENCE.txt,
 * which should have been included as part of the distribution containing this file.
 */
package org.goochjs.glicko2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * @author Jeremy Gooch
 *
 */
public class TestGlicko2 {

	private RatingCalculator ratingSystem = new RatingCalculator(0.06, 0.5);
	private RatingPeriodResults results = new RatingPeriodResults();
	private Rating player1 = new Rating("player1", ratingSystem); // the main player of Glickman's example
	private Rating player2 = new Rating("player2", ratingSystem); 
	private Rating player3 = new Rating("player3", ratingSystem);
	private Rating player4 = new Rating("player4", ratingSystem);
	private Rating player5 = new Rating("player5", ratingSystem); // this player won't compete during the test
	
	/**
	 * This test uses the values from the example towards the end of Glickman's paper as a simple test of the calculation engine
	 * In addition, we have another player who doesn't compete, in order to test that their deviation will have increased over time.
	 */
	@Test
	public void test() {
		// no init needed, default is: Rating 1500, RD 350, volatility 0.06.
//		initialise();
		printResults("Before");

		results.addResult(player1, player2); // player1 beats player 2
		results.addResult(player3, player1); // player3 beats player 1
		results.addResult(player4, player1); // player4 beats player 1
		results.addDraw(player4, player1); // Draw between player4 and player 1

		ratingSystem.updateRatings(results);
		
		printResults("After");

	}
	
	private void initialise() {

		player1.setRating(1500);
		player2.setRating(1400);
		player3.setRating(1550);
		player4.setRating(1700);
		
		player1.setRatingDeviation(200);
		player2.setRatingDeviation(30);
		player3.setRatingDeviation(100);
		player4.setRatingDeviation(300);

		results.addParticipants(player5);  // the other players will be added to the participants list automatically
	}

	private void printResults(String text) {
		System.out.println(text + "...");
		System.out.println(player1);
		System.out.println(player2);
		System.out.println(player3);
		System.out.println(player4);
		System.out.println(player5);
	}
}
