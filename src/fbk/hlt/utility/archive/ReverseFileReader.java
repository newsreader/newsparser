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

/**
 * Author: Christian Girardi (cgirardi@fbk.eu)
 * Date: 17-lug-2010
 */

import java.io.*;

public class ReverseFileReader {
    private RandomAccessFile randomfile = null;
    private long position;


    public ReverseFileReader (String filename) throws Exception {
        // Open up a random access file
        this.randomfile=new RandomAccessFile(filename,"rw");
        // Set our seek position to the end of the file
        this.position=this.randomfile.length();

        // Seek to the end of the file
        this.randomfile.seek(this.position);
        //Move our pointer to the first valid position at the end of the file.
        String thisLine=this.randomfile.readLine();
        while(thisLine == null ) {
            this.position--;

            if (this.position >= 0) {
                this.randomfile.seek(this.position);
                thisLine=this.randomfile.readLine();
                this.randomfile.seek(this.position);
            } else {
                break;
            }
        }
    }

    // Read one line from the current position towards the beginning
    public String readLine() throws Exception {
        int thisCode;
        char thisChar;
        String finalLine="";

        // If our position is less than zero already, we are at the beginning
        // with nothing to return.
        if ( this.position < 0 ) {
            return null;
        }

        for(;;) {
            //System.err.println(this.position + " " +finalLine);
            // we've reached the beginning of the file
            if ( this.position < 0 ) {
                break;
            }
            // Seek to the current position
            this.randomfile.seek(this.position);

            // Read the data at this position
            thisCode=this.randomfile.readByte();
            thisChar=(char)thisCode;

            // If this is a line break or carrige return, stop looking
            if (thisCode == 13 || thisCode == 10 ) {
                // See if the previous character is also a line break character.
                // this accounts for crlf combinations
                this.randomfile.seek(this.position-1);
                int nextCode=this.randomfile.readByte();
                if ( (thisCode == 10 && nextCode == 13) || (thisCode == 13 && nextCode == 10) ) {
                    // If we found another linebreak character, ignore it
                    this.position=this.position-1;
                }
                // Move the pointer for the next readline
                this.position--;
                break;
            } else {
                // This is a valid character append to the string
                finalLine=thisChar + finalLine;
            }
            // Move to the next char
            this.position--;
        }
        // return the line
        // return finalLine
        return new String(finalLine.getBytes("Latin1"),"UTF8");
    }

    public void close() throws IOException {
        if (randomfile != null) {
            randomfile.close();
            randomfile = null;
        }
    }

    public long getFilePointer() {
        return this.position;
    }

    public void setLength(long length) throws IOException {
        randomfile.setLength(length);
    }
}
