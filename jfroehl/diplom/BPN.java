/*
 *	BPN.java
 *
 *	Demo of a Backpropagation Net
 *	(Java application)
 *
 *	Takes names of capital towns as its input and learns the belonging country name.
 *	Called: java BPN <display step>
 *
 *	Written in 1997 by Jochen Froehlich.
 */


public class BPN {

	static BackpropagationNet bpn;

	/*
	 * compares two patterns and returns an accuracy value
	 */
	static String compare ( String recallPattern, String targetPattern ) {
		int acc = 0, len = recallPattern.length();
		char[] rp, tp = new char[len];
		rp = recallPattern.toCharArray();
		tp = targetPattern.toCharArray();
		for ( int i=0; i<len; i++ ) if ( rp[i] == tp[i] ) acc++;
		if ( acc == len ) return ( "  GOT IT!" );
		return ( "  (" + acc + "/" + len + ": " +  String.valueOf( (int)(acc*100)/len ) + "%)" );
	}


	/*
	 * shows the results
	 */
	static void output () {
		StringBuffer sb = new StringBuffer();
		String recall, accuracy;
		sb.append( "\n\rcycle: " + bpn.getLearningCycle() + "\n\n\r  input:       target:  output:    error:\n\r" );
		for ( int x=0; x<bpn.getNumberOfPatterns(); x++ )
			sb.append(	"  " + bpn.getInputPattern(x) + "   " + bpn.getTargetPattern(x) + "  " + bpn.getOutputPattern(x) + "    " + bpn.getPatternError(x) + "\n\r" );

		sb.append( "\n\r" );
		for ( int r=0; r<bpn.getNumberOfPatterns(); r++ ) {
			recall = bpn.recall( bpn.getInputPattern(r) );
			accuracy = compare( recall, bpn.getTargetPattern(r) );
			sb.append( "  recalling '" + bpn.getInputPattern(r) + "': " + recall + accuracy + "\n\r" );
		}

		double neterror = bpn.getError();
		String acc = (neterror>1.0) ? "2 bad" : String.valueOf((1.0-neterror)*100)+"%";
		sb.append( "\n\rminerror: " + bpn.getMinimumError() + "\n\rneterror: " + neterror + "\n\raccuracy: " + acc + "\n\rtime    : " + bpn.getElapsedTime() + " sec" );
		sb.append( "\n\r-----------------------------------------------------" );
		System.out.println( sb );
	}


	/*
	 * here's the action...
	 */
	public static void main ( String[] args ) {
		System.out.println( "\n\n\rDEMO OF A BACKPROPAGATION NET\n\r" );
		if ( args.length == 0 ) {
			System.out.println( "Missing argument --- Usage:  java BPN <display step>" );
			System.exit( 0 );
		}
		else {
			bpn = new BackpropagationNet();

			System.out.print( "Reading conversion file ..." );
			bpn.readConversionFile( "ascii2bin.cnv" );	// 1 ascii value -> 6 binary values
			System.out.println( "OK" );

			System.out.print( "Creating neuron layers ..." );
			bpn.addNeuronLayer( 10 );	// input layer for towns (must be 10, see pattern file)
			bpn.addNeuronLayer( 4 );	// hidden layer (number of neurons may vary)
										// (more hidden layers are possible)
			bpn.addNeuronLayer( 7 );	// output layer for countrys (must be 7, see pattern file)
			System.out.println( "OK" );

			System.out.print( "Connecting neuron layers ..." );
			bpn.connectLayers();
			System.out.println( "OK" );

			System.out.println( "\n\rNet structure:" );
			for ( int i=0; i<bpn.getNumberOfLayers(); i++ )
				System.out.println( "layer " + i + ": " + bpn.getNumberOfNeurons(i) + " neurons" );
			System.out.println( "weights: " + bpn.getNumberOfWeights() + "\n\r" );

			System.out.print( "Reading pattern file ..." );
			bpn.readPatternFile( "towns.pat" );
			System.out.println( "OK - patterns: " + bpn.getNumberOfPatterns() );

			// some optional method calls
			bpn.setLearningRate( 0.25 );
			bpn.setMinimumError( 0.0005 );
			bpn.setAccuracy( 0.2 );
			bpn.setMaxLearningCycles( -1 );	// -1 = no maximum cycle
			bpn.setDisplayStep( Integer.parseInt( args[0] ) );	// get the argument

			System.out.print( "\n\rallright, let's learn...\n\r" );
			bpn.resetTime();
			while ( !bpn.finishedLearning() ) {		// while the net learns
				bpn.learn();						// perform one learning step
				if ( bpn.displayNow() ) output();	// display the current results
			}
			output();
			System.out.println( "\n\rFINISHED." );
		}
	}
}
// EOC BPN


