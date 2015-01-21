/*******************************************************************************
 * Copyright 2013 EMBL-EBI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package htsjdk.samtools.cram.mask;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

public class DefaultReadMaskFactory implements ReadMaskFactory<String> {

    private static ReadMaskFactory<String> detectReadMaskFormat(InputStream is) throws IOException {
        BufferedReader bis = new BufferedReader(new InputStreamReader(is));
        String line = null;

        Pattern intMaskPattern = Pattern.compile("^[0-9\\s]+$");
        Pattern fastaMaskPattern = Pattern.compile("^[" + FastaByteArrayMaskFactory.DEFAULT_MASK_BYTE
                + FastaByteArrayMaskFactory.DEFAULT_NON_MASK_BYTE + "]+$");
        while ((line = bis.readLine()) != null) {
            if (line.length() == 0)
                continue;
            boolean intFormatMatches = intMaskPattern.matcher(line).matches();
            boolean fastaFormatMatches = fastaMaskPattern.matcher(line).matches();

            if (intFormatMatches && fastaFormatMatches)
                continue;

            if (intFormatMatches)
                return new IntegerListMaskFactory();

            return new FastaByteArrayMaskFactory();
        }
        return null;
    }

    @Override
    public PositionMask createMask(String line) throws ReadMaskFormatException {
        return null;
    }

}
