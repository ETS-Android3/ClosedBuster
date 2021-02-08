package jp.co.toshiba.iflink.imsif;

import android.content.res.XmlResourceParser;

import androidx.annotation.NonNull;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

public class SchemaXmlParser {
    private static final String XML_PROPERTY = "property";
    private static final String XML_NAME = "name";
    private static final String XML_TYPE = "type";
    private XmlPullParser mParser;
    private boolean mIsLoadXmlPersonalPropertyOnly;

    public SchemaXmlParser(@NonNull XmlResourceParser parser, boolean isLoadXmlPersonalPropertyOnly) {
        this.mParser = parser;
        this.mIsLoadXmlPersonalPropertyOnly = isLoadXmlPersonalPropertyOnly;
    }

    @NonNull
    public String parse() {
        StringBuilder sb = new StringBuilder();

        try {
            for(int eventType = this.mParser.getEventType(); eventType != 1; eventType = this.mParser.next()) {
                if (eventType == 2) {
                    if (!this.mIsLoadXmlPersonalPropertyOnly && this.mParser.getName().equals("schema")) {
                        sb.append("<schema name=\"");
                        sb.append(this.mParser.getAttributeValue((String)null, "name"));
                        sb.append("\">\n");
                    }

                    if (this.mParser.getName().equals("property")) {
                        String name = this.mParser.getAttributeValue((String)null, "name");
                        String type = this.mParser.getAttributeValue((String)null, "type");
                        this.putXmlData(sb, name, type);
                    }
                }
            }
        } catch (IOException | XmlPullParserException var5) {
            var5.printStackTrace();
        }

        if (!this.mIsLoadXmlPersonalPropertyOnly) {
            sb.append("</schema>\n");
        }

        return sb.toString();
    }

    private void putXmlData(@NonNull StringBuilder sb, @NonNull String name, @NonNull String type) {
        if (!this.mIsLoadXmlPersonalPropertyOnly || !name.equals("devicename") && !name.equals("deviceserial") && !name.equals("timestamp")) {
            sb.append("<property ").append("name").append("=\"").append(name).append("\" ").append("type").append("=\"").append(type).append("\" />\n");
        }
    }
}
