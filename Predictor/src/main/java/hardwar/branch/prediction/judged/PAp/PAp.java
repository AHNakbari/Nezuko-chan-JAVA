package hardwar.branch.prediction.judged.PAp;


import hardwar.branch.prediction.shared.*;
import hardwar.branch.prediction.shared.devices.*;
import org.graalvm.compiler.asm.sparc.SPARCAssembler;

import java.util.Arrays;

public class PAp implements BranchPredictor {

    private final int branchInstructionSize;

    private final ShiftRegister SC; // saturating counter register

    private final RegisterBank PABHR; // per address branch history register

    private final Cache<Bit[], Bit[]> PAPHT; // Per Address Predication History Table

    public PAp() {
        this(4, 2, 8);
    }

    public PAp(int BHRSize, int SCSize, int branchInstructionSize) {
        // TODO: complete the constructor
        this.branchInstructionSize = branchInstructionSize;

        // Initialize the PABHR with the given bhr and branch instruction size
        PABHR = new RegisterBank(branchInstructionSize, BHRSize);

        // Initializing the PAPHT with BranchInstructionSize as PHT Selector and 2^BHRSize row as each PHT entries
        // number and SCSize as block size
        PAPHT = new PerAddressPredictionHistoryTable(branchInstructionSize, 1 << BHRSize, SCSize);

        // Initialize the SC register
        SC = new SIPORegister("SC", SCSize, null);
    }
    @Override
    public BranchResult predict(BranchInstruction branchInstruction) {
        Bit[] bits = this.getCacheEntry(branchInstruction.getInstructionAddress(), PABHR.read(branchInstruction.getInstructionAddress()).read());
        PAPHT.putIfAbsent(bits, getDefaultBlock());
        SC.load(PAPHT.get(bits));
        if (SC.read()[0] == Bit.ONE) {
            return BranchResult.TAKEN;
        } else
            return BranchResult.NOT_TAKEN;
    }

    @Override
    public void update(BranchInstruction instruction, BranchResult actual) {
        Bit[] bits = CombinationalLogic.count(SC.read(), BranchResult.isTaken(actual), CountMode.SATURATING);
        Bit[] bits1 = this.getCacheEntry(instruction.getInstructionAddress(), PABHR.read(instruction.getInstructionAddress()).read());
        PAPHT.put(this.getCacheEntry(instruction.getInstructionAddress(), PABHR.read(instruction.getInstructionAddress()).read()), bits);
        // TODO
        // Shift all existing bits to the right by one position
//        for (int i = bits1.length - 1; i > 0; i--) {
//            bits1[i] = bits1[i - 1];
//        }
//
//         Insert the new bit at the beginning of the registe
//        bits1[0] = Bit.of(BranchResult.isTaken(actual));
        ShiftRegister s = PABHR.read(instruction.getInstructionAddress());
        s.insert(Bit.of(BranchResult.isTaken(actual)));
        PABHR.write(instruction.getInstructionAddress(), s.read());
    }


    private Bit[] getCacheEntry(Bit[] branchAddress, Bit[] BHRValue) {
        // Concatenate the branch address bits with the BHR bits
        Bit[] cacheEntry = new Bit[branchAddress.length + BHRValue.length];
        System.arraycopy(branchAddress, 0, cacheEntry, 0, branchInstructionSize);
        System.arraycopy(BHRValue, 0, cacheEntry, branchAddress.length, BHRValue.length);
        return cacheEntry;
    }

    /**
     * @return a zero series of bits as default value of cache block
     */
    private Bit[] getDefaultBlock() {
        Bit[] defaultBlock = new Bit[SC.getLength()];
        Arrays.fill(defaultBlock, Bit.ZERO);
        return defaultBlock;
    }

    @Override
    public String monitor() {
        return "PAp predictor snapshot: \n" + PABHR.monitor() + SC.monitor() + PAPHT.monitor();
    }
}
