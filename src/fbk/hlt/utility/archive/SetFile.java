/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fbk.hlt.utility.archive;

import java.util.*;
import java.io.*;

/**
 * Author: Christian Girardi (cgirardi@fbk.eu)
 * Date: 1-feb-2007


 Strategia:
 - tengo aperto il canale del file, mantengo all'inizio del file tre numeri per sapere:
 * il numero di chiavi ordinate
 * il numero di chiavi non ancora ordinate (che si trovano in fondo al file)
 * la posizione dopo l'ultima chiave ordinata (da dove vengono salvate le chiavi non ordinate)

 - mantengo un hash con le chiavi non ordinate per trovarle + velocemente. quando il file viene chiuso
 effettuo il merge ordinando tutte le chiavi.

 * 2010.02.24
 * - migliorato il get e il contains (utilizzo di cache e un binarySearch)
 * - per la normalizzazione delle chiavi uso new String(key.getBytes()) così vengono ordinate correttamente anche stringhe UTF8

 */


public class SetFile implements Iterable<String> {
    private Hashtable<String, String> newkeys = new Hashtable<String,String>();
    private LinkedHashMap<String,Long> goodjumpposition = new LinkedHashMap<String,Long>();
    private File file;
    private RandomAccessFile rafile;
    private boolean isReadable = false;
    private String[] cache = {"",""};
    private int countmergedfiles;
    private static final String SEPARATOR = "@";
    private long availableMemory = 0;
    private long unsortedPos = 0;
    private int unsortedSize = 0;
    //private static final String DELSEPARATOR = "§";

    /*private long heapMaxSize;
    private static int countHeapUse;
    private static int countHeapInit = 20;
    */

    boolean DEBUG = false;
    static final String RETURN_TAG = "<_n>";


    public SetFile(String filepath) throws Exception {
        file = new File(filepath);
        if (DEBUG)
            System.err.println("SETFILE: " +file);
        //if  (filepath.endsWith("webdatelogging.set"))
        //  DEBUG=true;

        if (!file.exists()) {
            this.clear();
        } else {
            try {
                if (file.canRead()) {
                    if (file.canWrite()) {
                        rafile = new RandomAccessFile(file.getCanonicalPath(),"rw");
                    } else {
                        rafile = new RandomAccessFile(file.getCanonicalPath(),"r");

                    }
                    isReadable = true;
                }

            } catch (IOException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }

        this.unsortedPos = this.readUnsortedPosition();
        this.unsortedSize = this.readUnsortedSize();
        if (unsortedSize == 0) {
            try {
                if (this.unsortedPos != rafile.length())
                    System.err.println("# WARNING! The file " + file + " is corrupted (maximum length exceed).");
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try {
                rafile.seek(unsortedPos);
                String line;
                while((line = rafile.readLine()) != null) {
                    if (line.lastIndexOf(SEPARATOR) > 0)
                        newkeys.put(line.substring(0,line.lastIndexOf(SEPARATOR)),line.substring(line.lastIndexOf(SEPARATOR) +1));
                }
                //controllo che le entry dell hash siano uguali a unsortedSize
                if (unsortedSize != newkeys.size()) {
                    System.err.println("# WARNING! The file " + file + " is corrupted (unsorted keys should be " + unsortedSize + ").");
                }
            } catch (IOException e) {
                System.err.println("# WARNING! The file " + file + " is corrupted (unsorted keys failed: " + unsortedSize + ").");
            }
            this.merge();
        }
        availableMemory = Runtime.getRuntime().freeMemory();
        setGoodJumps();
        //System.err.println("SetFile " + file + " (" +this.size() + "," + this.readUnsortedSize()+ "," +this.readUnsortedPosition()+")");
        /*heapMaxSize = Runtime.getRuntime().maxMemory();
        SetFile.countHeapUse = SetFile.countHeapInit - Long.numberOfTrailingZeros(heapMaxSize / Runtime.getRuntime().freeMemory());
        heapMaxSize = heapMaxSize / SetFile.countHeapInit;
        */
    }

    private void setGoodJumps () throws IOException {
        goodjumpposition.clear();
        if (this.size() > 10000) {
            rafile.seek(16);
            String line = rafile.readLine();
            goodjumpposition.put(line.substring(0,line.lastIndexOf(SEPARATOR)), (long) 16);
            int rate = (int) rafile.length() / 2000;
            for (int i=1; i < 2000; i++) {
                rafile.seek(rate * i);
                rafile.readLine();
                long pos = rafile.getFilePointer();
                line = rafile.readLine();
                goodjumpposition.put(line.substring(0,line.lastIndexOf(SEPARATOR)), pos);

            }
        }
        //System.err.println("goodjumpposition : " + goodjumpposition.size());
    }

    private long getMinPosition (String key) {
        long min = 0;
        for (Map.Entry entry : goodjumpposition.entrySet()) {
            if (((String) entry.getKey()).compareTo(key) < 0) {
                min = (Long) entry.getValue();
            } else {
                if (min != 0) {
                    //System.err.println("getMinPosition="+min + " " +key);
                    return min;
                } else {
                    break;
                }
            }
        }
        return 16;
    }

    private long getMaxPosition (String key) {
        return this.unsortedPos;
    }



    public boolean contains (String key) {
        if (get(key) != null) {
            return true;
        }
        return false;
    }

    public synchronized String get(String key) {
        if (key != null) {
            key = key.replaceAll("[\n|\r]+","").trim();
            if  (key.length() > 0) {
                key = new String(key.getBytes());
                //return this.binarySearch(key);
                if (cache[0].equals(key)) {
                    return cache[1];

                } else {
                    cache[0] = key;

                    //effettuo una ricerca binaria nei record salvati su file
                    cache[1] = this.binarySearch(key);
                    return cache[1];
                }
            }
        }
        return null;
    }



    public synchronized boolean put(String key, String value) throws Exception {
        if (key != null) {
            key = key.replaceAll("[\n|\r]+","").trim();
            if  (key.length() > 0) {
                key = new String(key.getBytes());


                //if (!this.contains(key)) {
                value = new String(value.getBytes());
                value= value.replaceAll("[\n|\r]+", RETURN_TAG).trim();
                if (DEBUG)
                    System.out.println("PUT " + key +", " + value + " ("+newkeys.size()+")");
                try {
                    rafile.seek(rafile.length());
                    rafile.writeBytes(key + SEPARATOR + value +"\n");

                    writeUnsortedSize(++unsortedSize);
                } catch (IOException e) {
                    return false;
                }
                cache[0] = key;
                cache[1] = value;
                try {
                    newkeys.put(key,value);
                    if (newkeys.size() % 1000 == 0 && Runtime.getRuntime().freeMemory() < availableMemory / 2) {
                        merge();
                    }

                } catch(Exception e) {
                    System.err.println("newkeys.size() = "+newkeys.size() );
                }

                return true;
                //}
            }

        }
        return false;
    }

    //merge hashfile and file already saved
    public synchronized void merge () throws Exception {
        if (newkeys.size() > 0) {
            //System.err.println("MERGE newkeys.size() = "+newkeys.size() +" Tot MEMORY=" + Runtime.getRuntime().totalMemory() +" FREE MEMORY=" + Runtime.getRuntime().freeMemory());
            if (DEBUG)
                System.err.println(countmergedfiles+ " Adding " + newkeys.size() + " keys");
            List<String> listaddedkeys = Collections.list(newkeys.keys());
            Collections.sort(listaddedkeys);
            countmergedfiles++;
            File outputMrg = new File(file.getCanonicalPath() + countmergedfiles + ".mrg");
            try {
                RandomAccessFile outra = new RandomAccessFile(outputMrg, "rw");

                int rasize = this.size();

                outra.writeInt(rasize);
                outra.writeInt(0);
                outra.writeLong(0);

                String line = rafile.readLine();
                int hashpos = 0;
                int compareCondition;
                String newline = "";
                int savedCounter = 0;
                while (isReadable) {
                    if (line == null || line.length() == 0 || this.unsortedPos < rafile.getFilePointer()) {
                        if (newline.length() > 0) {
                            savedCounter++;
                            outra.writeBytes(newline + "\n");
                        }

                        break;
                    } else {
                        if (hashpos < listaddedkeys.size()) {
                            int pos = line.lastIndexOf(SEPARATOR);
                            if (pos > 0 && listaddedkeys.get(hashpos) != null) {
                                compareCondition = line.substring(0,pos).compareTo((String) listaddedkeys.get(hashpos));
                                if (compareCondition < 0) {
                                    newline =line;
                                    line = rafile.readLine();
                                } else if (compareCondition > 0) {
                                    newline = listaddedkeys.get(hashpos) + SEPARATOR + newkeys.get(listaddedkeys.get(hashpos));
                                    hashpos++;
                                } else {
                                    newline = line.substring(0,pos) + SEPARATOR + newkeys.get(listaddedkeys.get(hashpos));
                                    hashpos++;
                                    line = rafile.readLine();
                                }
                            } else {
                                throw new Exception("# WARNING! The file " + file + " is corrupted (line separator is missed)\n" + rafile.getFilePointer() + ": " + line);
                            }
                        } else {
                            newline = line;
                            line = rafile.readLine();
                        }
                    }
                    savedCounter++;
                    outra.writeBytes(newline + "\n");
                    newline="";
                    //numkey++;
                    //System.err.println(a + " && " + hashpos + " >= " +  listaddedkeys.size());

                }
                for (int i=hashpos; i<listaddedkeys.size(); i++) {
                    savedCounter++;
                    outra.writeBytes(listaddedkeys.get(i) + SEPARATOR + newkeys.get(listaddedkeys.get(i)) +"\n");
                }

                this.unsortedPos = outra.getFilePointer();
                
                if (savedCounter != rasize) {
                    System.err.println("# WARNING! The size should be " + rasize + " ("+file.getCanonicalPath()+ " lines are " + savedCounter + ")");
                    outra.seek(0);
                    outra.writeInt(savedCounter);
                }
                outra.seek(8);
                outra.writeLong(this.unsortedPos);

                outra.close();

                rafile.close();
                rafile = null;

                if (file.delete()) {
                    if (!outputMrg.renameTo(file)) {
                        System.err.println("WARNING! Rename failed " + outputMrg);
                    }
                } else {
                    System.err.println("WARNING! The file "+file+" hasn't been managed well.");
                }

                reset();

                //System.err.println(file.delete() +"  merge kyb: " + );
                rafile = new RandomAccessFile(file,"rw");
                isReadable = true;
                
                setGoodJumps();
                newkeys.clear();
                this.unsortedSize = 0;
                this.writeUnsortedSize(this.unsortedSize);
                this.unsortedPos = this.readUnsortedPosition();


            } catch (FileNotFoundException e) {
                System.err.println("ERROR! " +e.getMessage());
            } catch (IOException e) {
                System.err.println("ERROR on merging " + file + " file: " +e.getMessage());
                //System.err.println(e.getMessage());
            } finally {
                if (outputMrg.exists()) {
                    outputMrg.delete();
                }
                availableMemory = Runtime.getRuntime().freeMemory();

            }
        }
    }

    private void reset() {
        Runtime.getRuntime().gc();
        //System.err.println("RESET newkeys.size() = "+newkeys.size() +" Tot MEMORY=" + Runtime.getRuntime().totalMemory() +" FREE MEMORY=" + Runtime.getRuntime().freeMemory());
    }


    // in this version there is an optimization per fare backward solo nel caso si passi per 2 volte dalla stessa linea
    // c'e` ancora qualcosa che non va (vedi test YouTubeDown cR4zRbPy2kY)

    // scaricare HGxbidvsg_g, x4DmiNalLvA, fdv21GPsyBQ
    private synchronized String binarySearch(String str) {
        if (!isReadable)
            return null;

        if (newkeys.containsKey(str)) {
            if (DEBUG)
                System.err.println("hash searching " +str);

            return newkeys.get(str);
        } else {
            try {

                String line, substr;

                long min = getMinPosition(str);
                long max = this.unsortedPos;
                //long max = getMaxPosition(str);
                long mid= (min+max) / 2;

                //parto da dove sono per risparmiare tempo
                //System.err.println (rafile.getFilePointer() + "> " + str);

                //se faccio la ricerca partendo dall'ultima posizione letta
                // (todo sembra che non si risparmi nulla anzi le prestazioni peggiorano leggermente, da RIVEDERE)
                /*line = rafile.readLine();
               if (line != null ) {
                   substr = line.substring(0,line.lastIndexOf(SEPARATOR));
                   if (str.equals(substr)) {
                       return line.substring(line.lastIndexOf(SEPARATOR) + SEPARATOR.length());
                   } else {
                       //System.err.println(line.substring(0,line.indexOf(SEPARATOR)) + ")).compareTo(" + str + ")");
                       if (substr.compareTo(str) < 0) {
                           //System.err.println(line + "> " + str);
                           min = rafile.getFilePointer();
                           max = this.unsortedPos;

                       } else {
                           //System.err.println(line + "< " + str + " " +min +"<=" + max + " && " + mid + " < " + (max - 1));
                           max = rafile.getFilePointer();

                       }
                       rafile.seek(min);
                   }
               } else {
                   max = this.unsortedPos();
               } */


                String lastline;
                long lastPos = min;
                if (DEBUG)
                    System.err.println("SEARCHING " +str);

                //controllo la prima riga
                rafile.seek(min);
                if ((line = rafile.readLine()) != null) {
                    if (line.lastIndexOf(SEPARATOR) > 0) {
                        if (str.equals(line.substring(0,line.lastIndexOf(SEPARATOR)))) {
                            if (DEBUG)
                                System.err.println("FOUND " +str);
                            return line.substring(line.lastIndexOf(SEPARATOR) + SEPARATOR.length());
                        }
                    }
                }

                while (mid > 16 && mid < (max - 1)) {
                    rafile.seek(mid);

                    if (lastPos != rafile.getFilePointer()) {
                        rafile.readLine();
                        lastPos = rafile.getFilePointer();
                        //if (goodjumpposition.size() < 10000)
                        //    goodjumpposition.put(mid, lastPos);
                    } else {
                        if (lastPos != 16) {
                            //System.out.println("lastPos " +lastPos + ", getFilePointer " + rafile.getFilePointer());
                            //if (mid != 16) {
                            long restline = 0;

                            while (true)  {
                                lastline = rafile.readLine();

                                if (lastline != null) {
                                    if(lastline.length() < restline || lastline.length() == 0) {
                                        break;
                                    } else {
                                        restline = restline + lastline.length();
                                    }
                                } else {
                                    break;
                                }
                                if (mid - restline < 16) {
                                    return null;
                                } else {
                                    rafile.seek(mid - restline);
                                }

                            }

                        }
                    }


                    if ((line = rafile.readLine()) != null) {
                        //if (DEBUG)
                        //  System.err.println("FIND " + mid + "," + rafile.getFilePointer() + " " + line);

                        if (line.lastIndexOf(SEPARATOR) > 0) {
                            substr = line.substring(0,line.lastIndexOf(SEPARATOR));

                            if (str.equals(substr)) {
                                if (DEBUG)
                                    System.err.println("FOUND " +str);
                                return line.substring(line.lastIndexOf(SEPARATOR) + SEPARATOR.length());
                            } else {
                                if (substr.compareTo(str) < 0) {
                                    //System.err.println(line + "> " + str);
                                    min = mid+1;
                                    //min = mid;
                                } else {
                                    //System.err.println(line + "< " + str + " " +min +"<=" + max + " && " + mid + " < " + (max - 1));
                                    max = mid-1;
                                    //max = mid;
                                }
                            }
                        }

                        if (max == rafile.getFilePointer()) {
                            break;
                        }
                        mid = (min+max) / 2;
                    } else {
                        break;
                    }


                }

            } catch (IOException e ) {
                //e.printStackTrace();
                System.err.println("Warning! Binary search error on file: " +file);
            }
        }
        return null;
    }

    //ricerca binaria di una linea che finisce per SEPARATOR + str
    private synchronized String binarySearch_LAST(String str) {
        if (!isReadable)
            return null;

        if (newkeys.containsKey(str)) {
            if (DEBUG)
                System.err.println("hash searching " +str);

            return newkeys.get(str);
        } else {
            try {

                String line = null, substr;

                long min = 16;
                long max = this.unsortedPos;
                long mid= (min+max) / 2;

                //parto da dove sono per risparmiare tempo
                //System.err.println (rafile.getFilePointer() + "> " + str);

                //se faccio la ricerca partendo dall'ultima posizione letta
                // (todo sembra che non si risparmi nulla anzi le prestazioni peggiorano leggermente, da RIVEDERE)
                /*line = rafile.readLine();
                if (line != null ) {
                    substr = line.substring(0,line.lastIndexOf(SEPARATOR));
                    if (str.equals(substr)) {
                        return line.substring(line.lastIndexOf(SEPARATOR) + SEPARATOR.length());
                    } else {
                        //System.err.println(line.substring(0,line.indexOf(SEPARATOR)) + ")).compareTo(" + str + ")");
                        if (substr.compareTo(str) < 0) {
                            //System.err.println(line + "> " + str);
                            min = rafile.getFilePointer();
                            max = this.unsortedPos;

                        } else {
                            //System.err.println(line + "< " + str + " " +min +"<=" + max + " && " + mid + " < " + (max - 1));
                            max = rafile.getFilePointer();

                        }
                        rafile.seek(min);
                    }
                } else {
                    max = this.unsortedPos;
                } */


                String lastline;

                if (DEBUG)
                    System.err.println("SEARCHING " +str);

                //controllo la prima riga
                rafile.seek(min);
                if ((line = rafile.readLine()) != null) {
                    if (line.lastIndexOf(SEPARATOR) > 0) {
                        substr = line.substring(0,line.lastIndexOf(SEPARATOR));

                        if (str.equals(substr)) {
                            if (DEBUG)
                                System.err.println("FOUND " +str);
                            return line.substring(line.lastIndexOf(SEPARATOR) + SEPARATOR.length());
                        }
                    }
                }


                while (mid > 16 && mid < (max - 1)) {
                    rafile.seek(mid);
                    //if (DEBUG)
                    //  System.err.println("FIRST " + mid + "," + rafile.getFilePointer() + " " + line);

                    long restline = 0;

                    while (true)  {
                        lastline = rafile.readLine();

                        if (lastline != null) {
                            if(lastline.length() < restline || lastline.length() == 0) {
                                break;
                            } else {
                                restline = restline + lastline.length();
                            }
                        } else {
                            break;
                        }
                        if (mid - restline < 16) {
                            return null;
                        } else {
                            rafile.seek(mid - restline);
                        }

                    }
                    //}

                    if ((line = rafile.readLine()) != null) {
                        //if (DEBUG)
                        //  System.err.println("FIND " + mid + "," + rafile.getFilePointer() + " " + line);

                        if (line.lastIndexOf(SEPARATOR) > 0) {
                            substr = line.substring(0,line.lastIndexOf(SEPARATOR));

                            if (str.equals(substr)) {
                                if (DEBUG)
                                    System.err.println("FOUND " +str);
                                return line.substring(line.lastIndexOf(SEPARATOR) + SEPARATOR.length());
                            } else {
                                if (substr.compareTo(str) < 0) {
                                    System.err.println(line + "> " + str);
                                    min = mid;
                                } else {
                                    System.err.println(line + "< " + str + " " +min +"<=" + max + " && " + mid + " < " + (max - 1));
                                    max = mid;
                                }
                            }
                        }

                        if (max == rafile.getFilePointer()) {
                            break;
                        }
                        mid = (min+max) / 2;
                    } else {
                        break;
                    }


                }

            } catch (IOException e ) {
                //e.printStackTrace();
                System.err.println("Warning! Binary search error on file: " +file);
            }
        }
        return null;
    }

    public void close() {
        try {
            //merge();
            isReadable = false;

            rafile.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized int size() {
        try {
            rafile.seek(0);
            return (rafile.readInt() + rafile.readInt());

        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private synchronized int readSize() {
        try {
            rafile.seek(0);
            return rafile.readInt();

        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private synchronized void writeSize(int num) {
        try {
            rafile.seek(0);
            rafile.writeInt(num);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized int readUnsortedSize() {
        try {
            rafile.seek(4);
            return rafile.readInt();

        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private synchronized void writeUnsortedSize(int num) {
        try {
            rafile.seek(4);
            rafile.writeInt(num);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized long readUnsortedPosition() {
        try {
            rafile.seek(8);
            return rafile.readLong();

        } catch (IOException e) {
            e.printStackTrace();
        }
        return 16;
    }

    private synchronized void writeUnsortedPosition(long num) {
        try {
            rafile.seek(8);
            rafile.writeLong(num);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String toString() {
        return file + " (" +this.readSize() + "," + this.readUnsortedSize() + ","+this.readUnsortedPosition()+")";
    }

    public synchronized void clear() {
        if (file != null) {
            try {
                if (rafile != null)
                    rafile.close();
                if (file.exists()) {
                    file.delete();
                    //System.err.println("Creating setfile: " + file.getCanonicalPath());
                }
                file.createNewFile();
                rafile = new RandomAccessFile(file.getCanonicalPath(),"rw");
                this.writeSize(0);
                this.writeUnsortedSize(0);
                this.writeUnsortedPosition(16);

                isReadable = true;

                //rafile.setLength(0);
                //System.err.println("Controllo lo stato di rafile (" + file.getName() + "): ");

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //Iterable interface
    public synchronized Iterator<String> iterator() {
        try {
            merge();
            rafile.seek(16);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new Iterator<String>() {
            long currpos = 16;
            public boolean hasNext() {
                try {
                    if (rafile.getFilePointer() != currpos)
                        rafile.seek(currpos);
                    //System.err.println("SetFile hasNext(): " +rafile.getFilePointer() +"<"+ rafile.length());
                    return (currpos < rafile.length());
                } catch (IOException e) {
                    return false;
                }
            }
            public String next() {
                try {
                    if (rafile.getFilePointer() != currpos)
                        rafile.seek(currpos);
                    String line=rafile.readLine();
                    currpos = rafile.getFilePointer();
                    //System.err.println("SetFile next(): " +rafile.getFilePointer() +" "+ line);
                    if (line.indexOf(SEPARATOR) > 0)
                        return line.substring(0, line.lastIndexOf(SEPARATOR));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    //test the class
    public static void main(String[] args) {
        // check parameters
        Calendar c = Calendar.getInstance();
        long todayInMillis = c.getTimeInMillis();
        int num = 0;
        int num_null = 0;
        try {
            if (args.length < 1) {
                System.err.println("No input file found.");
                return;
            } else if (args.length == 2 && args[0].equalsIgnoreCase("-l")) {
                SetFile setfile = new SetFile(args[1]);
                Iterator it = setfile.iterator();
                String key, val;

                while (it.hasNext()) {
                    key = (String) it.next();
                    val = setfile.get(key);
                    if(val == null)
                        num_null++;
                    System.out.println("> " +key + " " + val);
                    num++;
                }
                setfile.close();
            } else {

                HashMap<String,Integer> words = new HashMap<String,Integer>();
                SetFile setfile;
                String line,setvalue;
                //boolean alreadyMade = false;

                //se ho un file come secondo parametro carico le sue linne come entry da testare ,altrimenti uso il .set stesso
                String filein = args[0];
                boolean isSetFile = true;

                if (args.length > 1) {
                    filein = args[1];
                    isSetFile = false;
                }
                InputStreamReader insr = new InputStreamReader(new FileInputStream(new File(filein)),"UTF8");

                BufferedReader in = new BufferedReader(insr);
                in.readLine();
                while ((line = in.readLine()) != null) {
                    String key = line;
                    if (isSetFile)
                        key= line.substring(0,line.lastIndexOf(SEPARATOR));
                    if (key.length() > 0) {
                        // System.err.println(items[i]);
                        if (words.containsKey(key)) {
                            System.err.println("WARNING! Double value found... "+key);
                            //words.put(items[i],(Integer) words.get(items[i]) +1);
                        } else {
                            //System.err.println(items[i]);
                            if (isSetFile)
                                words.put(key, Integer.valueOf(line.substring(line.lastIndexOf(SEPARATOR)+SEPARATOR.length())));
                            else
                                words.put(key, -1);
                        }

                    }

                }
                in.close();
                insr.close();
                setfile = new SetFile(args[0]);
                // setfile.close();

                Set keyset = words.keySet();
                Iterator keys = keyset.iterator();
                int error =0;
                Date stime = new Date();
                int counter = 0;
                while(keys.hasNext()) {
                    line = (String) keys.next();
                    counter++;
                    if (counter % 100 == 0)
                        System.err.println(counter+"/"+keyset.size() + " " +line);

                    setvalue = setfile.get(line);
                    if (setvalue != null) {
                        if (words.get(line) != -1 && words.get(line) != Integer.parseInt(setvalue)) {
                            System.err.println(line + " " + setfile.get(line) + " != " +(Integer) words.get(line));
                            error++;
                        }
                    } else {
                        System.err.println("ERROR: Entry " + line + " not found.");
                        error++;
                    }
                }
                setfile.close();
                Date now = new Date();
                System.err.println("Time: " + (now.getTime() - stime.getTime()) + "ms");
                System.err.println(keyset.size() + " entries found");
                System.err.println("Error: " +error);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        c = Calendar.getInstance();
        System.err.println("Time: " + (c.getTimeInMillis() - todayInMillis) + " ("+num_null+"/"+num+" errors)");
    }

}
