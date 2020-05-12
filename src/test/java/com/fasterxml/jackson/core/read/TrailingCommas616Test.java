package com.fasterxml.jackson.core.read;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.json.JsonFactory;
import com.fasterxml.jackson.core.json.JsonReadFeature;

public class TrailingCommas616Test extends BaseTest
{
    final JsonFactory f = JsonFactory.builder()
            .enable(JsonReadFeature.ALLOW_MISSING_VALUES)
            .build();

    // [core#616]
    public void testRootLevelComma616() throws Exception
    {
        _testRootLevel616(f, MODE_READER);
    }

    public void testRootLevelComma616Bytes() throws Exception
    {
        _testRootLevel616(f, MODE_INPUT_STREAM);
        _testRootLevel616(f, MODE_INPUT_STREAM_THROTTLED);
    }

    public void testRootLevelComma616DataInput() throws Exception
    {
        _testRootLevel616(f, MODE_DATA_INPUT);
    }

    private void _testRootLevel616(JsonFactory f, int mode) throws Exception
    {
        JsonParser p = createParser(f, mode, ",");
        try {
            p.nextToken();
            fail("Should not pass");
        } catch (JsonParseException e) {
            verifyException(e, "Unexpected character (','");
        }
        p.close();
    }
}
