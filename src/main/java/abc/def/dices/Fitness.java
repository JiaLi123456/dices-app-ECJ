package abc.def.dices;

import ec.multiobjective.MultiObjectiveFitness;

public class Fitness extends ec.gp.koza.KozaFitness
{

    public boolean isIdealFitness()
    {
        if (standardizedFitness<0.8)
            return true;
        else
            return false;

    }
}
