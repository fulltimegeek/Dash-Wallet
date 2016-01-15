/*
 * Copyright 2013 Google Inc.
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

import org.bitcoinj.core.Utils;

import static com.google.common.base.Preconditions.checkState;

/**
 * Parameters for the old version 2 testnet. This is not useful to you - it exists only because some unit tests are
 * based on it.
 */
public class TestNet2Params extends AbstractBitcoinNetParams {
    public static final int TESTNET_MAJORITY_WINDOW = 100;
    public static final int TESTNET_MAJORITY_REJECT_BLOCK_OUTDATED = 75;
    public static final int TESTNET_MAJORITY_ENFORCE_BLOCK_UPGRADE = 51;

    public TestNet2Params() {
        super();
        id = ID_TESTNET;
        packetMagic = 0xcee2caffL;
        port = 19999;
        addressHeader = 139;
        p2shHeader = 19;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        interval = INTERVAL;
        targetTimespan = TARGET_TIMESPAN;
        maxTarget = Utils.decodeCompactBits(0x1e0ffff0L);
        dumpedPrivateKeyHeader = 239;
        genesisBlock.setTime(1390666206L);
        genesisBlock.setDifficultyTarget(0x1e0ffff0);
        genesisBlock.setNonce(3861367235l);
        spendableCoinbaseDepth = 100;
        subsidyDecreaseBlockCount = 210000;
        String genesisHash = genesisBlock.getHashAsString();
        checkState(genesisHash.equals("00000bafbc94add76cb75e2ec92894837288a481e5c005f6563d91623bf8bc2c"));
        dnsSeeds = new String[] {
                "testnet-seed.darkcoin.io",
                "testnet-seed.darkcoin.qa",       //ddoesn't seem active so I've commented it out
                "testnet-seed.darkcoin.io",
        };
        addrSeeds = null;
        bip32HeaderPub = 0x3a8061a0;
        bip32HeaderPriv = 0x3a805837;

        majorityEnforceBlockUpgrade = TESTNET_MAJORITY_ENFORCE_BLOCK_UPGRADE;
        majorityRejectBlockOutdated = TESTNET_MAJORITY_REJECT_BLOCK_OUTDATED;
        majorityWindow = TESTNET_MAJORITY_WINDOW;
    }

    private static TestNet2Params instance;
    public static synchronized TestNet2Params get() {
        if (instance == null) {
            instance = new TestNet2Params();
        }
        return instance;
    }

    @Override
    public String getPaymentProtocolId() {
        return null;
    }


}
