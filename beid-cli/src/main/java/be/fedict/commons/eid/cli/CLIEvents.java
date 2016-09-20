/*
 * Command line tool to get all the events and data form a beid
 * Made by Bruno
 */

package be.fedict.commons.eid.cli;

import java.math.BigInteger;

import javax.smartcardio.Card;
import javax.smartcardio.CardTerminal;

import be.fedict.commons.eid.client.BeIDCard;
import be.fedict.commons.eid.client.BeIDCards;
import be.fedict.commons.eid.client.BeIDCardManager;
import be.fedict.commons.eid.client.CancelledException;
import be.fedict.commons.eid.client.CardAndTerminalManager;
import be.fedict.commons.eid.client.FileType;
import be.fedict.commons.eid.client.event.BeIDCardEventsListener;
import be.fedict.commons.eid.client.event.CardEventsListener;
import be.fedict.commons.eid.client.event.CardTerminalEventsListener;
import be.fedict.commons.eid.consumer.Identity;
import be.fedict.commons.eid.consumer.tlv.TlvParser;
import be.fedict.commons.eid.client.FileType;
import be.fedict.commons.eid.consumer.Address;
import static com.sun.org.apache.xerces.internal.util.FeatureState.is;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Set;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.smartcardio.CardException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.StringUtils;
import sun.misc.IOUtils;
import org.json.JSONObject;

/*
 * mixed asynchronous detection of CardTerminals, BeID and non-BeID cards,
 * using a BeIDCardManager with your own CardAndTerminalManager
 */
public class CLIEvents implements BeIDCardEventsListener, CardEventsListener, CardTerminalEventsListener {
    private void start() throws InterruptedException {
        // -------------------------------------------------------------------------------------------------------
        // instantiate a CardAndTerminalManager with default settings (no
        // logging, default timeout)
        // -------------------------------------------------------------------------------------------------------
        final CardAndTerminalManager cardAndTerminalManager = new CardAndTerminalManager();

        // -------------------------------------------------------------------------------------------------------
        // instantiate a BeIDCardManager, pass it our CardAndTerminalManager to
        // use
        // -------------------------------------------------------------------------------------------------------
        final BeIDCardManager beIDCardManager = new BeIDCardManager(cardAndTerminalManager);

        // -------------------------------------------------------------------------------------------------------
        // register ourselves as BeIDCardEventsListener to get BeID card insert
        // and remove events
        // -------------------------------------------------------------------------------------------------------
        beIDCardManager.addBeIDCardEventListener(this);

        // -------------------------------------------------------------------------------------------------------
        // register ourselves as CardEventsListener to the BeIDCardManager, to
        // get events of *other* cards
        // being inserted/removed (if we would register ourselves to the
        // CardAndTerminalManager
        // for this, we would get 2 events when a BeID was inserted, one for the
        // BeID, one for the Card by itself,
        // because CardAndTerminalManager cannot distinguish between them, and
        // BeIDCardManager can)
        // -------------------------------------------------------------------------------------------------------
        beIDCardManager.addOtherCardEventListener(this);
        // ^^^^^^^^^^^^^^^ // see above

        // -------------------------------------------------------------------------------------------------------
        // register ourselves as CardTerminalEventsListener to get CardTerminal
        // attach and detach events
        // -------------------------------------------------------------------------------------------------------
        cardAndTerminalManager.addCardTerminalListener(this);

        // System.out
        //      .println("First, you'll see events for terminals and Cards that were already present");

        // -------------------------------------------------------------------------------------------------------
        // start the BeIDCardManager instance
        // -------------------------------------------------------------------------------------------------------
        beIDCardManager.start();

        // -------------------------------------------------------------------------------------------------------
        // start the CardAndTerminalManager
        // -------------------------------------------------------------------------------------------------------
        cardAndTerminalManager.start();

        // -------------------------------------------------------------------------------------------------------
        // the main thread goes off and does other things (for this example,
        // just loop and sleep)
        // -------------------------------------------------------------------------------------------------------
        for (;;) {
            Thread.sleep(2000);
        }
    }

    // ------------------------------------------------------------------------------------------------------------

    // ------------------------------------------------------------------------------------------------------------
    // these respond to CardTerminals being attached and detached
    // ------------------------------------------------------------------------------------------------------------

    @Override
    public void terminalAttached(final CardTerminal cardTerminal) {
        System.out.println(new JSONObject().put("event", "terminalAttached").toString());
    }

    @Override
    public void terminalDetached(final CardTerminal cardTerminal) {
        System.out.println(new JSONObject().put("event", "terminalDetached").toString());
    }

    @Override
    public void terminalEventsInitialized() {
        System.out.println(new JSONObject().put("event", "terminalEventsInitialized").toString());
    }

    // ------------------------------------------------------------------------------------------------------------
    // these respond to BeID cards being inserted and removed
    // ------------------------------------------------------------------------------------------------------------

    @Override
    public void eIDCardRemoved(final CardTerminal cardTerminal, final BeIDCard card) {
        System.out.println(new JSONObject().put("event", "eIDCardRemoved").toString());
    }

    @Override
    public void eIDCardInserted(final CardTerminal cardTerminal, final BeIDCard card) {
        System.out.println(new JSONObject().put("event", "eIDCardInserted").toString());

        try {
            getBeIDCardInfo(card);
        } catch (InterruptedException ex) {
            Logger.getLogger(CLIEvents.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(1);
        }
    }

    @Override
    public void eIDCardEventsInitialized() {
        System.out.println(new JSONObject().put("event", "eIDCardEventsInitialized").toString());
    }

    // ------------------------------------------------------------------------------------------------------------
    // these respond to non-BeID cards being inserted and removed
    // (because we registered with a BeIDCardManager, not a
    // CardAndTerminalManager)
    // ------------------------------------------------------------------------------------------------------------

    @Override
    public void cardInserted(final CardTerminal cardTerminal, final Card card) {
        if (card != null) {
            System.out.println(new JSONObject().put("event", "cardInserted").toString());
        }
    }

    @Override
    public void cardRemoved(final CardTerminal cardTerminal) {
        System.out.println(new JSONObject().put("event", "cardRemoved").toString());
    }

    @Override
    public void cardEventsInitialized() {
        System.out.println(new JSONObject().put("event", "cardEventsInitialized").toString());
    }

    public void getBeIDCardInfo(final BeIDCard card) throws InterruptedException {

            try {
                final byte[] idData = card.readFile(FileType.Identity);
                Identity id = TlvParser.parse(idData, Identity.class);

                final byte[] addressTLV = card.readFile(FileType.Address);
                final Address address = TlvParser.parse(addressTLV, Address.class);

                final byte[] photoFile = card.readFile(FileType.Photo);
                final String photoString = Base64.encodeBase64String(photoFile).trim();

                JSONObject output = new JSONObject();
                JSONObject data = new JSONObject();

                data
                    .put("cardNumber", id.cardNumber)
                    .put("chipNumber", id.chipNumber)
                    .put("cardValidityDateBegin", transformDate(id.cardValidityDateBegin))
                    .put("cardValidityDateEnd", transformDate(id.cardValidityDateEnd))
                    .put("cardDeliveryMunicipality", id.cardDeliveryMunicipality)
                    .put("nationalNumber", id.nationalNumber)
                    .put("firstName", id.firstName)
                    .put("name", id.name)
                    .put("middleName", id.middleName)
                    .put("nationality", id.nationality)
                    .put("placeOfBirth", id.placeOfBirth)
                    .put("dateOfBirth", transformDate(id.dateOfBirth))
                    .put("gender", id.gender)
                    .put("nobleCondition", id.nobleCondition)
                    .put("documentType", id.documentType)
                    .put("specialStatus", id.specialStatus)
                    .put("photoString", photoString)
                    .put("duplicate", id.duplicate)
                    .put("specialOrganisation", id.specialOrganisation)
                    .put("memberOfFamily", id.memberOfFamily)
                    .put("streetAndNumber", address.streetAndNumber)
                    .put("zip", address.zip)
                    .put("municipality", address.municipality);

                output
                    .put("event", "BeIDCardData")
                    .put("data", data);

                System.out.println(output.toString());
        } catch (final CardException cex) {
            cex.printStackTrace();
            System.exit(1);
        } catch (final IOException iox) {
            iox.printStackTrace();
            System.exit(1);
        }
    }

    protected static String transformDate(final Calendar calendar) {
        Date date = calendar.getTime();
        String formatted = new SimpleDateFormat("yyyy-MM-dd").format(date);

        return formatted;
        //String formatted = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(date);/** Transform Calendar to ISO 8601 string. */
        //return formatted.substring(0, 22) + ":" + formatted.substring(22);
    }

    // -------------------------------------------------------------------------------------------------------

    public static void main(final String[] args) throws InterruptedException {
        new CLIEvents().start();
    }

}
