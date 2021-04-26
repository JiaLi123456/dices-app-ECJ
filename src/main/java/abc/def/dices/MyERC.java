package abc.def.dices;
import ec.EvolutionState;
import ec.Problem;
import ec.gp.*;
import ec.util.Code;
import ec.util.DecodeReturn;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class MyERC extends ERC {
    public double value;
    public String toStringForHumans() { return "" + value; }
    public String encode() { return Code.encode(value); }
    public boolean decode(DecodeReturn dret) {
        int pos = dret.pos;
        String data = dret.data;
        Code.decode(dret);
        if (dret.type != DecodeReturn.T_DOUBLE) // uh oh! Restore and signal error.
        { dret.data = data; dret.pos = pos; return false; }
        value = dret.d;
        return true;
    }
    public boolean nodeEquals(GPNode node)
    { return (node.getClass() == this.getClass() && ((MyERC)node).value == value); }
    public void readNode(EvolutionState state, DataInput input) throws IOException
    { value = input.readDouble(); }
    public void writeNode(EvolutionState state, DataOutput output) throws IOException
    { output.writeDouble(value); }
    public void resetNode(EvolutionState state, int thread)
    {
        do value =Math.round( state.random[thread].nextDouble()*10);
        while (value <= 1.0 || value >= 10);
    }
    public void mutateERC(EvolutionState state, int thread) {
        double v;
        do v = value + state.random[thread].nextGaussian()*0.01;
        while( v <= 0.0 || v >= 10 );
        value = v;
    }
    public void eval(EvolutionState state, int thread, GPData input, ADFStack stack,
                     GPIndividual individual, Problem Problem)
    { ((DoubleData)input).x = value; }
}
