package amazon.checker.telegram;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Properties;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

public class AmazonCheckerTelegram {

    public static void main(String[] args) {
        try (InputStream settings = new FileInputStream("config.properties")) {
            //Settings loaded from config.properties
            Properties prop = new Properties();
            prop.load(settings);
            String tokenId = prop.getProperty("tokenId");
            String chatId = prop.getProperty("chatId");
            int delay = Integer.parseInt(prop.getProperty("delay")); //Recommended delay: > 6 seconds, prevent the system from kicking you out
            File items = new File(prop.getProperty("file"));
            
            //If the settings are not modified by user, throw an error
            if (tokenId.contains("insert") || chatId.contains("insert")) {
                System.err.println("[ERROR] Set the parameters 'tokenId' and 'chatId' in the file " + items);
                System.exit(1);
            } else { //If the tokenId is invalid, throw an error
                try {
                    Connection check = Jsoup.connect("https://api.telegram.org/bot" + tokenId + "/getMe").ignoreContentType(true);
                    check.get();
                } catch (HttpStatusException e) {
                    System.err.println("[ERROR] Invalid tokenId");
                    System.exit(2);
                } catch (UnknownHostException c) { //If the internet connection is invalid, thrown an error
                    System.err.println("[ERROR] No internet connection");
                    System.exit(3);
                }
            }
            //End of the settings code

            try {
                //Reading the items file
                String riga;
                ArrayList<String> links = new ArrayList<String>();
                BufferedReader br = new BufferedReader(new FileReader(items));
                while ((riga = br.readLine()) != null) {
                    links.add(riga);
                }
                br.close(); //Close the buffer
                //End of reading the items file

                while (true) { //Endless loop. Continue until code execution stops.
                    for (int i = 0; i < links.size(); i++) {
                        //Add the asin and the price to a string
                        String asin = links.get(i).split("\\|")[0];
                        String price = links.get(i).split("\\|")[1];
                        //End

                        //Make the connection to Amazon.it and scrape the useful text
                        //.userAgent = Mask the user agent to not be blocked by Amazon
                        //.ignoreHttpErrors(true) = Allows not to throw exceptions in case of page not found
                        Document doc = Jsoup.connect("https://www.amazon.it/dp/" + asin).userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36").ignoreHttpErrors(true).get();
                        Elements realPrice = doc.select("span#priceblock_ourprice"); //Normally displayed price
                        Elements availability = doc.select("div#availability"); //Availability of an item
                        Elements noBuyBox = doc.select("span#buybox-see-all-buying-choices"); //Offers not displayed by Amazon. Usually for high price or bad reputation sellers
                        Elements dealPrice = doc.select("span#priceblock_dealprice"); //Price for special deals and flash sales
                        //End of scraping

                        //Processing the scraped data
                        if (realPrice.hasText()) {
                            String realPriceS = realPrice.text().split(",")[0];

                            if (Integer.parseInt(realPriceS) < Integer.parseInt(price)) { //Check if the price exceeds the set maximum limit.
                                String message = doc.title() + "%0A%0A*" + realPrice.text() + "*%0A%0A" + availability.text() + "%0A%0AURL: " + "https://www.amazon.it/dp/" + asin;
                                sendMessage(tokenId, chatId, message, asin);
                            } else {
                                System.out.println("[" + asin + "] The price exceed the limit of " + price + " €. Item price: " + realPrice.text());
                            }

                        } else if (dealPrice.hasText()) {
                            String dealPriceS = dealPrice.text().split(",")[0];

                            if (Integer.parseInt(dealPriceS) < Integer.parseInt(price)) { //Check if the price exceeds the set maximum limit.
                                String message = doc.title() + "%0A%0A*" + dealPrice.text() + "*%0A%0A" + availability.text() + "%0A%0AURL: " + "https://www.amazon.it/dp/" + asin;
                                sendMessage(tokenId, chatId, message, asin);
                            } else {
                                System.out.println("[" + asin + "] The price exceed the limit of " + price + " €. Item price: " + dealPrice.text());
                            }

                        } else if (availability.hasText()) {
                            System.out.println("[" + asin + "] Item not available for purchase. Reason: " + availability.text());
                        } else if (noBuyBox.hasText()) {
                            System.out.println("[" + asin + "] Item has no buy box for purchase. Reason: " + noBuyBox.text());
                        } else { //Here to manage non-existent asins.
                            System.out.println("[" + asin + "] There is a problem. It seems that no product was found with this asin.");
                        }
                        //End of processing the scraped data

                        System.out.println("[Info] Waiting for delay... " + delay / 1000 + " seconds.");
                        Thread.sleep(delay); //Delay of your choice. The code pauses.
                    }
                }
            } catch (Exception e) { //In the event of an exception, notify via Telegram.
                e.printStackTrace();
                sendMessage(tokenId, chatId, "Exception", "Exception");
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Basic function to send a message to a specific chat on Telegram. Uses
     * JSoup.
     *
     * @param tokenId Bot of our Telegram bot
     * @param chatId ID of the Telegram chat
     * @param message Your message. Markup enabled
     * @param tag Surrounded by square brackets at the beginning of the console
     * message.
     */
    public static void sendMessage(String tokenId, String chatId, String message, String tag) {
        try {
            message = message.replaceAll("\\|", ""); //Avoid HTTP 400 error
            message = message.replaceAll("\\&", ""); //Avoid broken message
            Connection send = Jsoup.connect("https://api.telegram.org/bot" + tokenId + "/sendMessage?chat_id=" + chatId + "&text=" + message + "&parse_mode=markdown").ignoreContentType(true);
            send.get();
            System.out.println("[" + tag + "] " + "Message sent to " + chatId); //Only for log purpose.
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
