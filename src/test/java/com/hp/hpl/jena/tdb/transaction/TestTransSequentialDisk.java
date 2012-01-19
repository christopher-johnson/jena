/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hp.hpl.jena.tdb.transaction;

import org.junit.After;
import org.junit.Before;
import org.junit.Test ;
import org.openjena.atlas.lib.FileOps;

import com.hp.hpl.jena.query.ReadWrite ;
import com.hp.hpl.jena.tdb.ConfigTest;
import com.hp.hpl.jena.tdb.DatasetGraphTxn ;
import com.hp.hpl.jena.tdb.StoreConnection;
import com.hp.hpl.jena.tdb.sys.SystemTDB;

/** Basic tests and tests of ordering (single thread) */
public class TestTransSequentialDisk extends AbstractTestTransSeq
{
    static boolean nonDeleteableMMapFiles = SystemTDB.isWindows ;
    
    String DIR = null ;
    
    @Before public void before()
    {
        StoreConnection.reset() ;
        DIR = nonDeleteableMMapFiles ? ConfigTest.getTestingDirUnique() : ConfigTest.getTestingDir() ;
		FileOps.ensureDir(DIR) ;
		FileOps.clearDirectory(DIR) ;
    }

    @After public void after() {} 

    @Override
    protected StoreConnection getStoreConnection()
    {
        return StoreConnection.make(DIR) ;
    }
    
    @Test(expected=TDBTransactionException.class)
    public void trans_60()
    {
        // Expel.
        // Only applies to non-memory.
        StoreConnection sConn = getStoreConnection() ;
        DatasetGraphTxn dsgR1 = sConn.begin(ReadWrite.READ) ;
        StoreConnection.release(sConn.getLocation()) ;
    }

    @Test(expected=TDBTransactionException.class)
    public void trans_61()
    {
        // Expel.
        StoreConnection sConn = getStoreConnection() ;
        DatasetGraphTxn dsgR1 = sConn.begin(ReadWrite.WRITE) ;
        StoreConnection.release(sConn.getLocation()) ;
    }
    

}