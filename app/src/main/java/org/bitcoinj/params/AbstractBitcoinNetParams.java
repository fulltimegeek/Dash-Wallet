/*
 * Copyright 2013 Google Inc.
 * Copyright 2015 Andreas Schildbach
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitcoinj.params;

import android.util.Log;

import com.google.bitcoin.core.CoinDefinition;

import java.math.BigInteger;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.bitcoinj.core.BitcoinSerializer;

/**
 * Parameters for Bitcoin-like networks.
 */
public abstract class AbstractBitcoinNetParams extends NetworkParameters {
    /**
     * Scheme part for Bitcoin URIs.
     */
    public static final String BITCOIN_SCHEME = "bitcoin";

    private static final Logger log = LoggerFactory.getLogger(AbstractBitcoinNetParams.class);

    public AbstractBitcoinNetParams() {
        super();
    }

    /** 
     * Checks if we are at a difficulty transition point. 
     * @param storedPrev The previous stored block 
     * @return If this is a difficulty transition point 
     */
    protected boolean isDifficultyTransitionPoint(StoredBlock storedPrev) {
        return ((storedPrev.getHeight() + 1) % this.getInterval()) == 0;
    }

    final static String TAG = "AbstractBitcoinNetParams.java";

    @Override
    public void checkDifficultyTransitions(StoredBlock storedPrev, Block nextBlock,final BlockStore blockStore) throws BlockStoreException, VerificationException {
        //there used to be a LOCK right here
        int DiffMode = 1;
        if (getId().equals(NetworkParameters.ID_TESTNET)) {
            if (storedPrev.getHeight()+1 >= 16) { DiffMode = 4; }
        }
        else {
            if (storedPrev.getHeight()+1 >= 68589) { DiffMode = 4; }
            else if (storedPrev.getHeight()+1 >= 34140) { DiffMode = 3; }
            else if (storedPrev.getHeight()+1 >= 15200) { DiffMode = 2; }
        }

        if (DiffMode == 1) { checkDifficultyTransitions_V1(storedPrev, nextBlock,blockStore); return; }
        else if (DiffMode == 2) { checkDifficultyTransitions_V2(storedPrev, nextBlock, blockStore); return;}
        else if (DiffMode == 3) { DarkGravityWave(storedPrev, nextBlock, blockStore); return;}
        else if (DiffMode == 4) { DarkGravityWave3(storedPrev, nextBlock,blockStore); return; }

        DarkGravityWave3(storedPrev, nextBlock,blockStore);

        return;

    }

    private void checkDifficultyTransitions_V1(StoredBlock storedPrev, Block nextBlock,final BlockStore blockStore) throws BlockStoreException, VerificationException {
        //checkState(lock.isHeldByCurrentThread());
        Block prev = storedPrev.getHeader();

        // Is this supposed to be a difficulty transition point?
        if ((storedPrev.getHeight() + 1) % getInterval() != 0) {

            // TODO: Refactor this hack after 0.5 is released and we stop supporting deserialization compatibility.
            // This should be a method of the NetworkParameters, which should in turn be using singletons and a subclass
            // for each network type. Then each network can define its own difficulty transition rules.
            /*if (getId().equals(NetworkParameters.ID_TESTNET) && nextBlock.getTime().after(testnetDiffDate)) {
                checkTestnetDifficulty(storedPrev, prev, nextBlock);
                return;
            }*/

            // No ... so check the difficulty didn't actually change.
            if (nextBlock.getDifficultyTarget() != prev.getDifficultyTarget())
                throw new VerificationException("Unexpected change in difficulty at height " + storedPrev.getHeight() +
                        ": " + Long.toHexString(nextBlock.getDifficultyTarget()) + " vs " +
                        Long.toHexString(prev.getDifficultyTarget()));
            return;
        }

        // We need to find a block far back in the chain. It's OK that this is expensive because it only occurs every
        // two weeks after the initial block chain download.
        long now = System.currentTimeMillis();
        StoredBlock cursor = blockStore.get(prev.getHash());

        float blockstogoback = getInterval() - 1;
        if(storedPrev.getHeight()+1 != getInterval())
            blockstogoback = getInterval();

        for (int i = 0; i < blockstogoback; i++) {
            if (cursor == null) {
                // This should never happen. If it does, it means we are following an incorrect or busted chain.
                throw new VerificationException(
                        "Difficulty transition point but we did not find a way back to the genesis block.");
            }
            cursor = blockStore.get(cursor.getHeader().getPrevBlockHash());
        }
        long elapsed = System.currentTimeMillis() - now;
        if (elapsed > 50)
            log.info("Difficulty transition traversal took {}msec", elapsed);

        Block blockIntervalAgo = cursor.getHeader();
        int timespan = (int) (prev.getTimeSeconds() - blockIntervalAgo.getTimeSeconds());
        // Limit the adjustment step.
        final int targetTimespan = getTargetTimespan();
        if (timespan < targetTimespan / 4)
            timespan = targetTimespan / 4;
        if (timespan > targetTimespan * 4)
            timespan = targetTimespan * 4;

        BigInteger newDifficulty = Utils.decodeCompactBits(prev.getDifficultyTarget());
        newDifficulty = newDifficulty.multiply(BigInteger.valueOf(timespan));
        newDifficulty = newDifficulty.divide(BigInteger.valueOf(targetTimespan));

        if (newDifficulty.compareTo(CoinDefinition.proofOfWorkLimit) > 0) {
            log.info("Difficulty hit proof of work limit: {}", newDifficulty.toString(16));
            newDifficulty = CoinDefinition.proofOfWorkLimit;
        }

        int accuracyBytes = (int) (nextBlock.getDifficultyTarget() >>> 24) - 3;
        BigInteger receivedDifficulty = nextBlock.getDifficultyTargetAsInteger();

        // The calculated difficulty is to a higher precision than received, so reduce here.
        BigInteger mask = BigInteger.valueOf(0xFFFFFFL).shiftLeft(accuracyBytes * 8);
        newDifficulty = newDifficulty.and(mask);

        if (newDifficulty.compareTo(receivedDifficulty) != 0)
            throw new VerificationException("Network provided difficulty bits do not match what was calculated: " +
                    receivedDifficulty.toString(16) + " vs " + newDifficulty.toString(16));
    }

    @Override
    public Coin getMaxMoney() {
        return MAX_MONEY;
    }

    @Override
    public Coin getMinNonDustOutput() {
        return Transaction.MIN_NONDUST_OUTPUT;
    }

    @Override
    public MonetaryFormat getMonetaryFormat() {
        return new MonetaryFormat();
    }

    @Override
    public int getProtocolVersionNum(final ProtocolVersion version) {
        return version.getBitcoinProtocolVersion();
    }

    @Override
    public BitcoinSerializer getSerializer(boolean parseRetain) {
        return new BitcoinSerializer(this, parseRetain);
    }

    @Override
    public String getUriScheme() {
        return BITCOIN_SCHEME;
    }

    @Override
    public boolean hasMaxMoney() {
        return true;
    }

    private void DarkGravityWave1(StoredBlock storedPrev, Block nextBlock, final BlockStore blockStore) {
    /* current difficulty formula, limecoin - DarkGravity, written by Evan Duffield - evan@limecoin.io */
        StoredBlock BlockLastSolved = storedPrev;
        StoredBlock BlockReading = storedPrev;
        Block BlockCreating = nextBlock;
        //BlockCreating = BlockCreating;
        long nBlockTimeAverage = 0;
        long nBlockTimeAveragePrev = 0;
        long nBlockTimeCount = 0;
        long nBlockTimeSum2 = 0;
        long nBlockTimeCount2 = 0;
        long LastBlockTime = 0;
        long PastBlocksMin = 14;
        long PastBlocksMax = 140;
        long CountBlocks = 0;
        BigInteger PastDifficultyAverage = BigInteger.valueOf(0);
        BigInteger PastDifficultyAveragePrev = BigInteger.valueOf(0);

        //if (BlockLastSolved == NULL || BlockLastSolved->nHeight == 0 || BlockLastSolved->nHeight < PastBlocksMin) { return bnProofOfWorkLimit.GetCompact(); }
        if (BlockLastSolved == null || BlockLastSolved.getHeight() == 0 || (long)BlockLastSolved.getHeight() < PastBlocksMin)
        { verifyDifficulty(CoinDefinition.proofOfWorkLimit, storedPrev, nextBlock); }

        for (int i = 1; BlockReading != null && BlockReading.getHeight() > 0; i++) {
            if (PastBlocksMax > 0 && i > PastBlocksMax) { break; }
            CountBlocks++;

            if(CountBlocks <= PastBlocksMin) {
                if (CountBlocks == 1) { PastDifficultyAverage = BlockReading.getHeader().getDifficultyTargetAsInteger(); }
                else
                {
                    //PastDifficultyAverage = ((CBigNum().SetCompact(BlockReading->nBits) - PastDifficultyAveragePrev) / CountBlocks) + PastDifficultyAveragePrev;
                    PastDifficultyAverage = BlockReading.getHeader().getDifficultyTargetAsInteger().subtract(PastDifficultyAveragePrev).divide(BigInteger.valueOf(CountBlocks)).add(PastDifficultyAveragePrev);

                }
                PastDifficultyAveragePrev = PastDifficultyAverage;
            }

            if(LastBlockTime > 0){
                long Diff = (LastBlockTime - BlockReading.getHeader().getTimeSeconds());
                if(Diff < 0) Diff = 0;
                if(nBlockTimeCount <= PastBlocksMin) {
                    nBlockTimeCount++;

                    if (nBlockTimeCount == 1) { nBlockTimeAverage = Diff; }
                    else { nBlockTimeAverage = ((Diff - nBlockTimeAveragePrev) / nBlockTimeCount) + nBlockTimeAveragePrev; }
                    nBlockTimeAveragePrev = nBlockTimeAverage;
                }
                nBlockTimeCount2++;
                nBlockTimeSum2 += Diff;
            }
            LastBlockTime = BlockReading.getHeader().getTimeSeconds();

            //if (BlockReading->pprev == NULL)
            try {
                StoredBlock BlockReadingPrev = blockStore.get(BlockReading.getHeader().getPrevBlockHash());
                if (BlockReadingPrev == null)
                {
                    //assert(BlockReading); break;
                    return;
                }
                BlockReading = BlockReadingPrev;
            }
            catch(BlockStoreException x)
            {
                return;
            }
        }

        BigInteger bnNew = PastDifficultyAverage;
        if (nBlockTimeCount != 0 && nBlockTimeCount2 != 0) {
            double SmartAverage = (((nBlockTimeAverage)*0.7)+((nBlockTimeSum2 / nBlockTimeCount2)*0.3));
            if(SmartAverage < 1) SmartAverage = 1;
            double Shift = CoinDefinition.TARGET_SPACING/SmartAverage;

            long nActualTimespan = (long)((CountBlocks*CoinDefinition.TARGET_SPACING)/Shift);
            long nTargetTimespan = (CountBlocks* CoinDefinition.TARGET_SPACING);
            if (nActualTimespan < nTargetTimespan/3)
                nActualTimespan = nTargetTimespan/3;
            if (nActualTimespan > nTargetTimespan*3)
                nActualTimespan = nTargetTimespan*3;

            // Retarget
            bnNew = bnNew.multiply(BigInteger.valueOf(nActualTimespan));
            bnNew = bnNew.divide(BigInteger.valueOf(nTargetTimespan));
        }
        verifyDifficulty(bnNew, storedPrev, nextBlock);

        /*if (bnNew > bnProofOfWorkLimit){
            bnNew = bnProofOfWorkLimit;
        }
        return bnNew.GetCompact();*/
    }

    private void verifyDifficulty(BigInteger calcDiff, StoredBlock storedPrev, Block nextBlock)
    {
        if (calcDiff.compareTo(CoinDefinition.proofOfWorkLimit) > 0) {
            log.info("Difficulty hit proof of work limit: {}", calcDiff.toString(16));
            calcDiff = CoinDefinition.proofOfWorkLimit;
        }
        int accuracyBytes = (int) (nextBlock.getDifficultyTarget() >>> 24) - 3;
        BigInteger receivedDifficulty = nextBlock.getDifficultyTargetAsInteger();

        // The calculated difficulty is to a higher precision than received, so reduce here.
        BigInteger mask = BigInteger.valueOf(0xFFFFFFL).shiftLeft(accuracyBytes * 8);
        calcDiff = calcDiff.and(mask);
        if(getId().compareTo(NetworkParameters.ID_TESTNET) == 0)
        {
            if (calcDiff.compareTo(receivedDifficulty) != 0)
                throw new VerificationException("Network provided difficulty bits do not match what was calculated: " +
                        receivedDifficulty.toString(16) + " vs " + calcDiff.toString(16));
        }
        else
        {



            int height = storedPrev.getHeight() + 1;
            ///if(System.getProperty("os.name").toLowerCase().contains("windows"))
            //{
            if(height <= 68589)
            {
                long nBitsNext = nextBlock.getDifficultyTarget();

                long calcDiffBits = (accuracyBytes+3) << 24;
                calcDiffBits |= calcDiff.shiftRight(accuracyBytes*8).longValue();

                double n1 = ConvertBitsToDouble(calcDiffBits);
                double n2 = ConvertBitsToDouble(nBitsNext);




                if(java.lang.Math.abs(n1-n2) > n1*0.2)
                    throw new VerificationException("Network provided difficulty bits do not match what was calculated: " +
                            receivedDifficulty.toString(16) + " vs " + calcDiff.toString(16));


            }
            else
            {
                if (calcDiff.compareTo(receivedDifficulty) != 0)
                    throw new VerificationException("Network provided difficulty bits do not match what was calculated: " +
                            receivedDifficulty.toString(16) + " vs " + calcDiff.toString(16));
            }



            /*
            if(height >= 34140)
                {
                    long nBitsNext = nextBlock.getDifficultyTarget();
                    long calcDiffBits = (accuracyBytes+3) << 24;
                    calcDiffBits |= calcDiff.shiftRight(accuracyBytes*8).longValue();
                    double n1 = ConvertBitsToDouble(calcDiffBits);
                    double n2 = ConvertBitsToDouble(nBitsNext);
                    if(height <= 45000) {
                        if(java.lang.Math.abs(n1-n2) > n1*0.2)
                            throw new VerificationException("Network provided difficulty bits do not match what was calculated: " +
                                    receivedDifficulty.toString(16) + " vs " + calcDiff.toString(16));
                    }
                    else if(java.lang.Math.abs(n1-n2) > n1*0.005)
                        throw new VerificationException("Network provided difficulty bits do not match what was calculated: " +
                                receivedDifficulty.toString(16) + " vs " + calcDiff.toString(16));
                }
                else
                {
                    if (calcDiff.compareTo(receivedDifficulty) != 0)
                        throw new VerificationException("Network provided difficulty bits do not match what was calculated: " +
                                receivedDifficulty.toString(16) + " vs " + calcDiff.toString(16));
                }
            */

            //}
            /*else
            {
            if(height >= 34140 && height <= 45000)
            {
                long nBitsNext = nextBlock.getDifficultyTarget();
                long calcDiffBits = (accuracyBytes+3) << 24;
                calcDiffBits |= calcDiff.shiftRight(accuracyBytes*8).longValue();
                double n1 = ConvertBitsToDouble(calcDiffBits);
                double n2 = ConvertBitsToDouble(nBitsNext);
                if(java.lang.Math.abs(n1-n2) > n1*0.2)
                    throw new VerificationException("Network provided difficulty bits do not match what was calculated: " +
                            receivedDifficulty.toString(16) + " vs " + calcDiff.toString(16));
            }
            else
            {
                if (calcDiff.compareTo(receivedDifficulty) != 0)
                    throw new VerificationException("Network provided difficulty bits do not match what was calculated: " +
                            receivedDifficulty.toString(16) + " vs " + calcDiff.toString(16));
            }
            }*/
        }
    }

    static double ConvertBitsToDouble(long nBits){
        long nShift = (nBits >> 24) & 0xff;

        double dDiff =
                (double)0x0000ffff / (double)(nBits & 0x00ffffff);

        while (nShift < 29)
        {
            dDiff *= 256.0;
            nShift++;
        }
        while (nShift > 29)
        {
            dDiff /= 256.0;
            nShift--;
        }

        return dDiff;
    }

    private void checkDifficultyTransitions_V2(StoredBlock storedPrev, Block nextBlock,final BlockStore blockStore) throws BlockStoreException, VerificationException {
        final long      	BlocksTargetSpacing			= (long)(2.5 * 60); // 10 minutes
        int         		TimeDaySeconds				= 60 * 60 * 24;
        long				PastSecondsMin				= TimeDaySeconds / 40;
        long				PastSecondsMax				= TimeDaySeconds * 7;
        long				PastBlocksMin				= PastSecondsMin / BlocksTargetSpacing;   //? blocks
        long				PastBlocksMax				= PastSecondsMax / BlocksTargetSpacing;   //? blocks

        //if(!fgw.isNativeLibraryLoaded())
        //long start = System.currentTimeMillis();
        KimotoGravityWell(storedPrev, nextBlock, BlocksTargetSpacing, PastBlocksMin, PastBlocksMax, blockStore);
        //long end1 = System.currentTimeMillis();
        //if(kgw.isNativeLibraryLoaded())
        //else
        //    KimotoGravityWell_N(storedPrev, nextBlock, BlocksTargetSpacing, PastBlocksMin, PastBlocksMax);
        //long end2 = System.currentTimeMillis();
        //if(kgw.isNativeLibraryLoaded())
        //    KimotoGravityWell_N2(storedPrev, nextBlock, BlocksTargetSpacing, PastBlocksMin, PastBlocksMax);
        /*long end3 = System.currentTimeMillis();
        long java = end1 - start;
        long n1 = end2 - end1;
        long n2 = end3 - end2;
        if(i > 20)
        {
            j += java;
            N += n1;
            N2 += n2;
            if(i != 0 && ((i % 10) == 0))
             //log.info("KGW 10 blocks: J={}; N={} -%.0f%; N2={} -%.0f%", java, n1, ((double)(java-n1))/java*100, n2, ((double)(java-n2))/java*100);
                 log.info("KGW {} blocks: J={}; N={} -{}%; N2={} -{}%", i-20, j, N, ((double)(j-N))/j*100, N2, ((double)(j-N2))/j*100);
        }
        ++i;*/
    }

    private void KimotoGravityWell(StoredBlock storedPrev, Block nextBlock, long TargetBlocksSpacingSeconds, long PastBlocksMin, long PastBlocksMax,BlockStore blockStore)  throws BlockStoreException, VerificationException {
	/* current difficulty formula, megacoin - kimoto gravity well */
        //const CBlockIndex  *BlockLastSolved				= pindexLast;
        //const CBlockIndex  *BlockReading				= pindexLast;
        //const CBlockHeader *BlockCreating				= pblock;
        StoredBlock         BlockLastSolved             = storedPrev;
        StoredBlock         BlockReading                = storedPrev;
        Block               BlockCreating               = nextBlock;

        BlockCreating				= BlockCreating;
        long				PastBlocksMass				= 0;
        long				PastRateActualSeconds		= 0;
        long				PastRateTargetSeconds		= 0;
        double				PastRateAdjustmentRatio		= 1f;
        BigInteger			PastDifficultyAverage = BigInteger.valueOf(0);
        BigInteger			PastDifficultyAveragePrev = BigInteger.valueOf(0);;
        double				EventHorizonDeviation;
        double				EventHorizonDeviationFast;
        double				EventHorizonDeviationSlow;

        long start = System.currentTimeMillis();

        if (BlockLastSolved == null || BlockLastSolved.getHeight() == 0 || (long)BlockLastSolved.getHeight() < PastBlocksMin)
        { verifyDifficulty(CoinDefinition.proofOfWorkLimit, storedPrev, nextBlock); }

        int i = 0;
        long LatestBlockTime = BlockLastSolved.getHeader().getTimeSeconds();

        for (i = 1; BlockReading != null && BlockReading.getHeight() > 0; i++) {
            if (PastBlocksMax > 0 && i > PastBlocksMax) { break; }
            PastBlocksMass++;

            if (i == 1)	{ PastDifficultyAverage = BlockReading.getHeader().getDifficultyTargetAsInteger(); }
            else		{ PastDifficultyAverage = ((BlockReading.getHeader().getDifficultyTargetAsInteger().subtract(PastDifficultyAveragePrev)).divide(BigInteger.valueOf(i)).add(PastDifficultyAveragePrev)); }
            PastDifficultyAveragePrev = PastDifficultyAverage;


            if (BlockReading.getHeight() > 646120 && LatestBlockTime < BlockReading.getHeader().getTimeSeconds()) {
                //eliminates the ability to go back in time
                LatestBlockTime = BlockReading.getHeader().getTimeSeconds();
            }

            PastRateActualSeconds			= BlockLastSolved.getHeader().getTimeSeconds() - BlockReading.getHeader().getTimeSeconds();
            PastRateTargetSeconds			= TargetBlocksSpacingSeconds * PastBlocksMass;
            PastRateAdjustmentRatio			= 1.0f;
            if (BlockReading.getHeight() > 646120){
                //this should slow down the upward difficulty change
                if (PastRateActualSeconds < 5) { PastRateActualSeconds = 5; }
            }
            else {
                if (PastRateActualSeconds < 0) { PastRateActualSeconds = 0; }
            }
            if (PastRateActualSeconds != 0 && PastRateTargetSeconds != 0) {
                PastRateAdjustmentRatio			= (double)PastRateTargetSeconds / PastRateActualSeconds;
            }
            EventHorizonDeviation			= 1 + (0.7084 * java.lang.Math.pow((Double.valueOf(PastBlocksMass)/Double.valueOf(28.2)), -1.228));
            EventHorizonDeviationFast		= EventHorizonDeviation;
            EventHorizonDeviationSlow		= 1 / EventHorizonDeviation;

            if (PastBlocksMass >= PastBlocksMin) {
                if ((PastRateAdjustmentRatio <= EventHorizonDeviationSlow) || (PastRateAdjustmentRatio >= EventHorizonDeviationFast))
                {
                    /*assert(BlockReading)*/;
                    break;
                }
            }
            StoredBlock BlockReadingPrev = blockStore.get(BlockReading.getHeader().getPrevBlockHash());
            if (BlockReadingPrev == null)
            {
                //assert(BlockReading);
                //Since we are using the checkpoint system, there may not be enough blocks to do this diff adjust, so skip until we do
                //break;
                return;
            }
            BlockReading = BlockReadingPrev;
        }

        /*CBigNum bnNew(PastDifficultyAverage);
        if (PastRateActualSeconds != 0 && PastRateTargetSeconds != 0) {
            bnNew *= PastRateActualSeconds;
            bnNew /= PastRateTargetSeconds;
        } */
        //log.info("KGW-J, {}, {}, {}", storedPrev.getHeight(), i, System.currentTimeMillis() - start);
        BigInteger newDifficulty = PastDifficultyAverage;
        if (PastRateActualSeconds != 0 && PastRateTargetSeconds != 0) {
            newDifficulty = newDifficulty.multiply(BigInteger.valueOf(PastRateActualSeconds));
            newDifficulty = newDifficulty.divide(BigInteger.valueOf(PastRateTargetSeconds));
        }

        if (newDifficulty.compareTo(CoinDefinition.proofOfWorkLimit) > 0) {
            log.info("Difficulty hit proof of work limit: {}", newDifficulty.toString(16));
            newDifficulty = CoinDefinition.proofOfWorkLimit;
        }


        //log.info("KGW-j Difficulty Calculated: {}", newDifficulty.toString(16));
        verifyDifficulty(newDifficulty, storedPrev, nextBlock);

    }

    private void DarkGravityWave(StoredBlock storedPrev, Block nextBlock,final BlockStore blockStore) {
    /* current difficulty formula, limecoin - DarkGravity, written by Evan Duffield - evan@limecoin.io */
        StoredBlock BlockLastSolved = storedPrev;
        StoredBlock BlockReading = storedPrev;
        Block BlockCreating = nextBlock;
        //BlockCreating = BlockCreating;
        long nBlockTimeAverage = 0;
        long nBlockTimeAveragePrev = 0;
        long nBlockTimeCount = 0;
        long nBlockTimeSum2 = 0;
        long nBlockTimeCount2 = 0;
        long LastBlockTime = 0;
        long PastBlocksMin = 14;
        long PastBlocksMax = 140;
        long CountBlocks = 0;
        BigInteger PastDifficultyAverage = BigInteger.valueOf(0);
        BigInteger PastDifficultyAveragePrev = BigInteger.valueOf(0);

        //if (BlockLastSolved == NULL || BlockLastSolved->nHeight == 0 || BlockLastSolved->nHeight < PastBlocksMin) { return bnProofOfWorkLimit.GetCompact(); }
        if (BlockLastSolved == null || BlockLastSolved.getHeight() == 0 || (long)BlockLastSolved.getHeight() < PastBlocksMin)
        { verifyDifficulty(CoinDefinition.proofOfWorkLimit, storedPrev, nextBlock); }

        for (int i = 1; BlockReading != null && BlockReading.getHeight() > 0; i++) {
            if (PastBlocksMax > 0 && i > PastBlocksMax)
            {
                break;
            }
            CountBlocks++;

            if(CountBlocks <= PastBlocksMin) {
                if (CountBlocks == 1) { PastDifficultyAverage = BlockReading.getHeader().getDifficultyTargetAsInteger(); }
                else
                {
                    //PastDifficultyAverage = ((CBigNum().SetCompact(BlockReading->nBits) - PastDifficultyAveragePrev) / CountBlocks) + PastDifficultyAveragePrev;
                    PastDifficultyAverage = BlockReading.getHeader().getDifficultyTargetAsInteger().subtract(PastDifficultyAveragePrev).divide(BigInteger.valueOf(CountBlocks)).add(PastDifficultyAveragePrev);

                }
                PastDifficultyAveragePrev = PastDifficultyAverage;
            }

            if(LastBlockTime > 0){
                long Diff = (LastBlockTime - BlockReading.getHeader().getTimeSeconds());
                //if(Diff < 0)
                //   Diff = 0;
                if(nBlockTimeCount <= PastBlocksMin) {
                    nBlockTimeCount++;

                    if (nBlockTimeCount == 1) { nBlockTimeAverage = Diff; }
                    else { nBlockTimeAverage = ((Diff - nBlockTimeAveragePrev) / nBlockTimeCount) + nBlockTimeAveragePrev; }
                    nBlockTimeAveragePrev = nBlockTimeAverage;
                }
                nBlockTimeCount2++;
                nBlockTimeSum2 += Diff;
            }
            LastBlockTime = BlockReading.getHeader().getTimeSeconds();

            //if (BlockReading->pprev == NULL)
            try {
                StoredBlock BlockReadingPrev = blockStore.get(BlockReading.getHeader().getPrevBlockHash());
                if (BlockReadingPrev == null)
                {
                    //assert(BlockReading); break;
                    return;
                }
                BlockReading = BlockReadingPrev;
            }
            catch(BlockStoreException x)
            {
                return;
            }
        }

        BigInteger bnNew = PastDifficultyAverage;
        if (nBlockTimeCount != 0 && nBlockTimeCount2 != 0) {
            double SmartAverage = ((((double)nBlockTimeAverage)*0.7)+(((double)nBlockTimeSum2 / (double)nBlockTimeCount2)*0.3));
            if(SmartAverage < 1) SmartAverage = 1;
            double Shift = CoinDefinition.TARGET_SPACING/SmartAverage;

            double fActualTimespan = (((double)CountBlocks*(double)CoinDefinition.TARGET_SPACING)/Shift);
            double fTargetTimespan = ((double)CountBlocks*CoinDefinition.TARGET_SPACING);
            if (fActualTimespan < fTargetTimespan/3)
                fActualTimespan = fTargetTimespan/3;
            if (fActualTimespan > fTargetTimespan*3)
                fActualTimespan = fTargetTimespan*3;

            long nActualTimespan = (long)fActualTimespan;
            long nTargetTimespan = (long)fTargetTimespan;

            // Retarget
            bnNew = bnNew.multiply(BigInteger.valueOf(nActualTimespan));
            bnNew = bnNew.divide(BigInteger.valueOf(nTargetTimespan));
        }
        verifyDifficulty(bnNew, storedPrev, nextBlock);

        /*if (bnNew > bnProofOfWorkLimit){
            bnNew = bnProofOfWorkLimit;
        }
        return bnNew.GetCompact();*/
    }

    private void DarkGravityWave3(StoredBlock storedPrev, Block nextBlock, BlockStore blockStore) {
        /* current difficulty formula, darkcoin - DarkGravity v3, written by Evan Duffield - evan@darkcoin.io */
        StoredBlock BlockLastSolved = storedPrev;
        StoredBlock BlockReading = storedPrev;
        Block BlockCreating = nextBlock;
        BlockCreating = BlockCreating;
        long nActualTimespan = 0;
        long LastBlockTime = 0;
        long PastBlocksMin = 24;
        long PastBlocksMax = 24;
        long CountBlocks = 0;
        BigInteger PastDifficultyAverage = BigInteger.ZERO;
        BigInteger PastDifficultyAveragePrev = BigInteger.ZERO;

        if (BlockLastSolved == null || BlockLastSolved.getHeight() == 0 || BlockLastSolved.getHeight() < PastBlocksMin) {
            verifyDifficulty(CoinDefinition.proofOfWorkLimit, storedPrev, nextBlock);
            return;
        }

        for (int i = 1; BlockReading != null && BlockReading.getHeight() > 0; i++) {
            if (PastBlocksMax > 0 && i > PastBlocksMax) { break; }
            CountBlocks++;

            if(CountBlocks <= PastBlocksMin) {
                if (CountBlocks == 1) { PastDifficultyAverage = BlockReading.getHeader().getDifficultyTargetAsInteger(); }
                else { PastDifficultyAverage = ((PastDifficultyAveragePrev.multiply(BigInteger.valueOf(CountBlocks)).add(BlockReading.getHeader().getDifficultyTargetAsInteger()).divide(BigInteger.valueOf(CountBlocks + 1)))); }
                PastDifficultyAveragePrev = PastDifficultyAverage;
            }

            if(LastBlockTime > 0){
                long Diff = (LastBlockTime - BlockReading.getHeader().getTimeSeconds());
                nActualTimespan += Diff;
            }
            LastBlockTime = BlockReading.getHeader().getTimeSeconds();

            try {
                StoredBlock BlockReadingPrev = blockStore.get(BlockReading.getHeader().getPrevBlockHash());
                if (BlockReadingPrev == null)
                {
                    //assert(BlockReading); break;
                    return;
                }
                BlockReading = BlockReadingPrev;
            }
            catch(BlockStoreException x)
            {
                return;
            }
        }

        BigInteger bnNew= PastDifficultyAverage;

        long nTargetTimespan = CountBlocks*TARGET_SPACING;//nTargetSpacing;

        if (nActualTimespan < nTargetTimespan/3)
            nActualTimespan = nTargetTimespan/3;
        if (nActualTimespan > nTargetTimespan*3)
            nActualTimespan = nTargetTimespan*3;

        // Retarget
        bnNew = bnNew.multiply(BigInteger.valueOf(nActualTimespan));
        bnNew = bnNew.divide(BigInteger.valueOf(nTargetTimespan));
        verifyDifficulty(bnNew, storedPrev, nextBlock);

    }
}
