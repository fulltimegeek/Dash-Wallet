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

import org.bitcoinj.core.*;
import org.bitcoinj.net.discovery.*;

import java.net.*;

import static com.google.common.base.Preconditions.*;

/**
 * Parameters for the main production network on which people trade goods and services.
 */
public class MainNetParams extends AbstractBitcoinNetParams {
    public static final int MAINNET_MAJORITY_WINDOW = 1000;
    public static final int MAINNET_MAJORITY_REJECT_BLOCK_OUTDATED = 950;
    public static final int MAINNET_MAJORITY_ENFORCE_BLOCK_UPGRADE = 750;

    final static String TAG = "MainNetParams.java";
    public MainNetParams() {
        super();
        interval = INTERVAL;
        targetTimespan = TARGET_TIMESPAN;
        maxTarget = Utils.decodeCompactBits(0x1e0ffff0L);
        dumpedPrivateKeyHeader = 204; //BASE58_prefix SECRET_KEY
        addressHeader = 76; //BASE58_prefix PUBKEY_ADDR
        p2shHeader = 16; //BASE58_prefix SCRIPT_ADDR
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        port = 9999;
        packetMagic = 0xbf0c6bbdL;//0xbf0c6bbdL
        bip32HeaderPub = 0x002fe52f8; //The 4 byte header that serializes in base58 to "drkv".
        bip32HeaderPriv = 0x02fe52cc; //The 4 byte header that serializes in base58 to "drkp"

        majorityEnforceBlockUpgrade = MAINNET_MAJORITY_ENFORCE_BLOCK_UPGRADE;
        majorityRejectBlockOutdated = MAINNET_MAJORITY_REJECT_BLOCK_OUTDATED;
        majorityWindow = MAINNET_MAJORITY_WINDOW;

        genesisBlock.setDifficultyTarget(0x1e0ffff0L);
        genesisBlock.setTime(1390095618L);
        genesisBlock.setNonce(28917698);
        id = ID_MAINNET;
        subsidyDecreaseBlockCount = 210000;
        spendableCoinbaseDepth = 100;
        String genesisHash = genesisBlock.getHashAsString();
        String blk = genesisHash.toString();
        checkState(genesisHash.equals("00000ffd590b1485b3caadc19b22e6379c733355108f107a430458cdf3407ab6"),genesisHash);

        // This contains (at a minimum) the blocks which are not BIP30 compliant. BIP30 changed how duplicate
        // transactions are handled. Duplicated transactions could occur in the case where a coinbase had the same
        // extraNonce and the same outputs but appeared at different heights, and greatly complicated re-org handling.
        // Having these here simplifies block connection logic considerably.
        checkpoints.put(1500, Sha256Hash.wrap("000000aaf0300f59f49bc3e970bad15c11f961fe2347accffff19d96ec9778e3"));
        checkpoints.put(4991, Sha256Hash.wrap("000000003b01809551952460744d5dbb8fcbd6cbae3c220267bf7fa43f837367"));
        checkpoints.put(9918, Sha256Hash.wrap("00000000213e229f332c0ffbe34defdaa9e74de87f2d8d1f01af8d121c3c170b"));
        checkpoints.put(16912, Sha256Hash.wrap("00000000075c0d10371d55a60634da70f197548dbbfa4123e12abfcbc5738af9"));
        checkpoints.put(23912, Sha256Hash.wrap("0000000000335eac6703f3b1732ec8b2f89c3ba3a7889e5767b090556bb9a276"));
        checkpoints.put(35457, Sha256Hash.wrap("0000000000b0ae211be59b048df14820475ad0dd53b9ff83b010f71a77342d9f"));
        checkpoints.put(45479, Sha256Hash.wrap("000000000063d411655d590590e16960f15ceea4257122ac430c6fbe39fbf02d"));
        checkpoints.put(55895, Sha256Hash.wrap("0000000000ae4c53a43639a4ca027282f69da9c67ba951768a20415b6439a2d7"));
        checkpoints.put(68899, Sha256Hash.wrap("0000000000194ab4d3d9eeb1f2f792f21bb39ff767cb547fe977640f969d77b7"));
        checkpoints.put(74619, Sha256Hash.wrap("000000000011d28f38f05d01650a502cc3f4d0e793fbc26e2a2ca71f07dc3842"));
        checkpoints.put(75095, Sha256Hash.wrap("0000000000193d12f6ad352a9996ee58ef8bdc4946818a5fec5ce99c11b87f0d"));
        checkpoints.put(88805, Sha256Hash.wrap("00000000001392f1652e9bf45cd8bc79dc60fe935277cd11538565b4a94fa85f"));
        checkpoints.put(107996, Sha256Hash.wrap("00000000000a23840ac16115407488267aa3da2b9bc843e301185b7d17e4dc40"));
        checkpoints.put(137993, Sha256Hash.wrap("00000000000cf69ce152b1bffdeddc59188d7a80879210d6e5c9503011929c3c"));
        checkpoints.put(167996, Sha256Hash.wrap("000000000009486020a80f7f2cc065342b0c2fb59af5e090cd813dba68ab0fed"));
        checkpoints.put(207992, Sha256Hash.wrap("00000000000d85c22be098f74576ef00b7aa00c05777e966aff68a270f1e01a5"));
        checkpoints.put(312645, Sha256Hash.wrap("0000000000059dcb71ad35a9e40526c44e7aae6c99169a9e7017b7d84b1c2daf"));

        dnsSeeds = new String[] {
                //"dnsseed.darkcoin.io",      //doesn't seem active so I've commented it out
                "dnsseed.darkcoin.qa",
                "dnsseed.masternode.io",
                //"dnsseed.dashpay.io",       //doesn't seem active so I've commented it out
        };
        httpSeeds = new HttpDiscovery.Details[] {
                // Mike Hearn
                /*new HttpDiscovery.Details(
                        ECKey.fromPublicOnly(Utils.HEX.decode("027a79143a4de36341494d21b6593015af6b2500e720ad2eda1c0b78165f4f38c4")),
                        URI.create("http://main.seed.vinumeris.com/peers")
                ),
                // Andreas Schildbach
                new HttpDiscovery.Details(
                        ECKey.fromPublicOnly(Utils.HEX.decode("0238746c59d46d5408bf8b1d0af5740fe1a6e1703fcb56b2953f0b965c740d256f")),
                        URI.create("http://httpseed.bitcoin.schildbach.de/peers")
                )*/
        };

        addrSeeds = new int[] {
                0xB95CDCBF, 0xBCE2B8F0, 0x50F08C18, 0xB23E6AA3, 0x80C7D59C, 0x05876814
        };
    }

    private static MainNetParams instance;
    public static synchronized MainNetParams get() {
        if (instance == null) {
            instance = new MainNetParams();
        }
        return instance;
    }

    @Override
    public String getPaymentProtocolId() {
        return PAYMENT_PROTOCOL_ID_MAINNET;
    }
}
