package jp.iflink.closed_buster.util;

import android.content.Context;
import android.util.Log;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import jp.iflink.closed_buster.model.SensorInfo;

public class XmlUtil {

    private static final String TAG = "XmlUtil";
    private static LogUseUtil logUseUtil = new LogUseUtil();

    // xml読み込み
    public static List<SensorInfo> readXml(Context applicationContext){
        // ログ差込
        ////loguseutil.specific(TAG, "XmlUtil readXml：START", "DEBUG", 3);

        // ファイルハンドラを生成
        FileHandler fileHandler = new FileHandler(applicationContext);
        // センサー定義ファイルを取得
        File sensorsFile = fileHandler.getFile("sensors.xml");

        // ログ差込
        ////loguseutil.specific(TAG, "XmlUtil readXml：filePath： " + file, "DEBUG", 3);

        List<SensorInfo> sensorList = new ArrayList<>();

        if (!sensorsFile.exists()){
            // センサー定義ファイルが無い場合は処理中断
            return sensorList;
        }
        try {
            // DocumentBuilderを生成
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = factory.newDocumentBuilder();

            // ファイルを渡してdocumentを生成
            Document document = documentBuilder.parse(sensorsFile);

            // ルート要素の取得
            Element rootElement = document.getDocumentElement();

            // sensorsノードを取得する
            NodeList sensorsNodes = rootElement.getChildNodes();

            // 子ノードでループ
            for (int idx = 0; idx < sensorsNodes.getLength(); idx++) {
                // sensorノードを取得する
                Node sensorNode = sensorsNodes.item(idx);
                // sensorノードの判定
                if (sensorNode.getNodeType() == Node.ELEMENT_NODE && sensorNode.getNodeName().equals("sensor")) {
                    // id属性値を取得する
                    Integer sensorId = findSensorId(sensorNode);
                    // センサーの作成
                    SensorInfo sensor = createSensor(sensorNode);
                    if (sensorId != null){
                        // id属性値の指定の方を優先
                        sensor.setId(sensorId);
                    }
                    // センサーリストに追加
                    sensorList.add(sensor);
                }
            }
        } catch (ParserConfigurationException | IOException | SAXException e) {
            Log.e(TAG, e.getMessage(), e);
        }

        // ログ差込
        ////loguseutil.specific(TAG, "XmlUtil readXml：END", "DEBUG", 3);

        return sensorList;
    }

    private static Integer findSensorId(Node sensorNode){
        Integer sensorId = null;
        String sensorIdStr= ((Element)sensorNode).getAttribute("id");
        if (sensorIdStr != null && !sensorIdStr.isEmpty()) {
            try {
                sensorId = Integer.valueOf(sensorIdStr);
            } catch (NumberFormatException e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
        return sensorId;
    }

    // sensor情報の生成
    private static SensorInfo createSensor(Node sensorNode){

        // ログ差込
        ////loguseutil.specific(TAG, "XmlUtil setXml：START", "DEBUG", 3);

        // sensor情報
        SensorInfo sensorInfo = new SensorInfo();

        // sensorノードの項目ノードリストの取得
        NodeList itemNodeList = sensorNode.getChildNodes();

        // Sensorノードでループ
        for (int idx = 0; idx < itemNodeList.getLength(); idx++) {
            // Sensor内のノード
            Node nodeItem = itemNodeList.item(idx);
            // Sensorノードで分岐
            if (nodeItem.getNodeType() == Node.ELEMENT_NODE) {
                if (nodeItem.getNodeName().equals("id")) {
                    // idのセット
                    //System.out.println("id：" + nodeItem.getTextContent());
                    sensorInfo.setId(Integer.parseInt(nodeItem.getTextContent()));

                } else if (nodeItem.getNodeName().equals("bdaddress")) {
                    // BDアドレスのセット
                    //System.out.println("BDAddress:" + nodeItem.getTextContent());
                    sensorInfo.setBdAddress(nodeItem.getTextContent());

                } else if (nodeItem.getNodeName().equals("room")) {
                    // 部屋名のセット
                    //System.out.println("room:" + nodeItem.getTextContent());
                    sensorInfo.setRoom(nodeItem.getTextContent());
                }
            }
        }

        // ログ差込
        ////loguseutil.specific(TAG, "XmlUtil setXml：END", "DEBUG", 3);

        return sensorInfo;
    }

}
