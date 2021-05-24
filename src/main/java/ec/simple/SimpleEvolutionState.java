/*
  Copyright 2006 by Sean Luke
  Licensed under the Academic Free License version 3.0
  See the file "LICENSE" for more information
*/


package ec.simple;
import abc.def.dices.SearchRunner;
import ec.*; import abc.def.dices.SearchRunner;
import ec.util.Checkpoint;

/* 
 * SimpleEvolutionState.java
 * 
 * Created: Tue Aug 10 22:14:46 1999
 * By: Sean Luke
 */

/**
 * A SimpleEvolutionState is an EvolutionState which implements a simple form
 * of generational evolution.
 *
 * <p>First, all the individuals in the population are created.
 * <b>(A)</b>Then all individuals in the population are evaluated.
 * Then the population is replaced in its entirety with a new population
 * of individuals bred from the old population.  Goto <b>(A)</b>.
 *
 * <p>Evolution stops when an ideal individual is found (if quitOnRunComplete
 * is set to true), or when the number of generations (loops of <b>(A)</b>)
 * exceeds the parameter value numGenerations.  Each generation the system
 * will perform garbage collection and checkpointing, if the appropriate
 * parameters were set.
 *
 * <p>This approach can be readily used for
 * most applications of Genetic Algorithms and Genetic Programming.
 *
 * @author Sean Luke
 * @version 1.0 
 */

public class SimpleEvolutionState extends EvolutionState
    {
    public void startFresh(SearchRunner runner)
        {
       // output.message("Setting up");
       // System.out.println(this.toString());
        setup(this,null,runner);  // a garbage Parameter

        // POPULATION INITIALIZATION
       // output.message("Initializing Generation 0");
        statistics.preInitializationStatistics(this);
        population = initializer.initialPopulation(this, 0); // unthreaded
        statistics.postInitializationStatistics(this);

        // INITIALIZE CONTACTS -- done after initialization to allow
        // a hook for the user to do things in Initializer before
        // an attempt is made to connect to island models etc.
        exchanger.initializeContacts(this);
        evaluator.initializeContacts(this);
        }

    public int evolve()
        {
        if (generation > 0) 
            //output.message("Generation " + generation +"\tEvaluations So Far " + evaluations);

        // EVALUATION
////////////////

           // long time1 = System.currentTimeMillis();
        {
            long time1 = System.currentTimeMillis();
            statistics.preEvaluationStatistics(this);
            long time2 = System.currentTimeMillis();
           // System.out.println("preEvaluationStatistics: "+(time2-time1));
        }
            long time11 = System.currentTimeMillis();
        evaluator.evaluatePopulation(this);
            long time21 = System.currentTimeMillis();
            System.out.println("EvaluationPopulation: "+(time21-time11));
            long time12 = System.currentTimeMillis();
        statistics.postEvaluationStatistics(this);
            long time22 = System.currentTimeMillis();
           // System.out.println("postEvaluationStatistics: "+(time22-time12));

        // SHOULD WE QUIT?
        String runCompleteMessage = evaluator.runComplete(this);
        if ((runCompleteMessage != null) && quitOnRunComplete)
            {
            //output.message(runCompleteMessage);
            return R_SUCCESS;
            }

        // SHOULD WE QUIT?
        if ((numGenerations != UNDEFINED && generation >= numGenerations-1) ||
            (numEvaluations != UNDEFINED && evaluations >= numEvaluations))
            {
            return R_FAILURE;
            }
 
        // INCREMENT GENERATION AND CHECKPOINT
        generation++;
       
        // PRE-BREEDING EXCHANGING

        statistics.prePreBreedingExchangeStatistics(this);

        population = exchanger.preBreedingExchangePopulation(this);

        statistics.postPreBreedingExchangeStatistics(this);


        String exchangerWantsToShutdown = exchanger.runComplete(this);
        if (exchangerWantsToShutdown!=null)
            { 
            //output.message(exchangerWantsToShutdown);
            return R_SUCCESS;
            }

            long time3 = System.currentTimeMillis();
        // BREEDING
        statistics.preBreedingStatistics(this);
        population = breeder.breedPopulation(this);
        statistics.postBreedingStatistics(this);
            long time4 = System.currentTimeMillis();
            System.out.println("breedPopulation: "+(time4-time3));

        // POST-BREEDING EXCHANGING
        statistics.prePostBreedingExchangeStatistics(this);
        population = exchanger.postBreedingExchangePopulation(this);
        statistics.postPostBreedingExchangeStatistics(this);


        if (checkpoint && (generation - 1) % checkpointModulo == 0) 
            {
            output.message("Checkpointing");
            statistics.preCheckpointStatistics(this);
            Checkpoint.setCheckpoint(this);
            statistics.postCheckpointStatistics(this);
            }

        return R_NOTDONE;
        }

    /**
     * @param result
     */
    public void finish(int result) 
        {
        output.message("Total Evaluations " + evaluations);
        /* finish up -- we completed. */
        statistics.finalStatistics(this,result);
        finisher.finishPopulation(this,result);
        exchanger.closeContacts(this,result);
        evaluator.closeContacts(this,result);
        }

    }
