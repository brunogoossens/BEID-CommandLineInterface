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

/*
 * mixed asynchronous detection of CardTerminals, BeID and non-BeID cards,
 * using a BeIDCardManager with your own CardAndTerminalManager
 */
public class CLIEvents
		implements
			BeIDCardEventsListener,
			CardEventsListener,
			CardTerminalEventsListener {
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
		final BeIDCardManager beIDCardManager = new BeIDCardManager(
				cardAndTerminalManager);

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
		//		.println("First, you'll see events for terminals and Cards that were already present");

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
                System.out.println("{\"action\":\"terminalAttached\"}");
		// System.out.println("CardTerminal [" + cardTerminal.getName()
		//		+ "] attached\n");
	}

	@Override
	public void terminalDetached(final CardTerminal cardTerminal) {
                System.out.println("{\"action\":\"terminalDetached\"}");
		//System.out.println("CardTerminal [" + cardTerminal.getName()
		//		+ "] detached\n");
	}

	@Override
	public void terminalEventsInitialized() {
		// System.out
		//		.println("From now on you'll see terminals being Attached/Detached");
	}

	// ------------------------------------------------------------------------------------------------------------
	// these respond to BeID cards being inserted and removed
	// ------------------------------------------------------------------------------------------------------------

	@Override
	public void eIDCardRemoved(final CardTerminal cardTerminal,
			final BeIDCard card) {
                System.out.println("{\"event\":\"cardRemoved\"}");
		//System.out.println("BeID Card Removed From Card Termimal ["
		//		+ cardTerminal.getName() + "]\n");
	}

	@Override
	public void eIDCardInserted(final CardTerminal cardTerminal,
			final BeIDCard card) {
                        System.out.println("{\"event\":\"cardInsered\"}");
		//System.out.println("BeID Card Inserted Into Card Termimal ["
		//		+ cardTerminal.getName() + "]\n");
            try {
                cardInfo(card);
            } catch (InterruptedException ex) {
                Logger.getLogger(CLIEvents.class.getName()).log(Level.SEVERE, null, ex);
            }
	}

	@Override
	public void eIDCardEventsInitialized() {
		// System.out
		// 		.println("From now on you'll see BeID Cards being Inserted/Removed");
	}

	// ------------------------------------------------------------------------------------------------------------
	// these respond to non-BeID cards being inserted and removed
	// (because we registered with a BeIDCardManager, not a
	// CardAndTerminalManager)
	// ------------------------------------------------------------------------------------------------------------

	@Override
	public void cardInserted(final CardTerminal cardTerminal, final Card card) {
		if (card != null) {
                        System.out.println("{\"event\":\"invalidBeidCard\"}");
			// System.out.println("Other Card ["
			//		+ String.format("%x", new BigInteger(1, card.getATR()
			//				.getBytes())) + "] Inserted Into Terminal ["
			//		+ cardTerminal.getName() + "]");
		} else {
                        System.out.println("{\"event\":\"invalidBeidCard\"}");
			//System.out.println("Other Card Inserted Into Terminal ["
			//		+ cardTerminal.getName() + "] but failed to connect()");
		}
	}

	@Override
	public void cardRemoved(final CardTerminal cardTerminal) {
                System.out.println("{\"event\":\"invalidBeidCardRemoved\"}");
		// System.out.println("Other Card Removed From [" + cardTerminal.getName()
		//		+ "]");
	}

	@Override
	public void cardEventsInitialized() {
		// System.out
		//		.println("From now on you'll see Non-BeID Cards being Inserted/Removed");
	}
        
        public void cardInfo(final BeIDCard card) throws InterruptedException {

            try {
                    final byte[] idData = card.readFile(FileType.Identity);
                    final Identity id = TlvParser.parse(idData, Identity.class);
                    
                    byte[] identityTLV=card.readFile(FileType.Identity);
		    byte[] addressTLV=card.readFile(FileType.Address);
		
                    Address address=TlvParser.parse(addressTLV,Address.class);
                    
                    final byte[] photoFile = card.readFile(FileType.Photo);
                    String image = Base64.encodeBase64String(photoFile).trim();

                    System.out.println(
                        "{"
                            + "\"event\":\"cardData\","
                            + "\"data\":{"
                                + "\"cardNumber\":\"" + id.cardNumber + "\","
                                + "\"chipNumber\":\"" + id.chipNumber + "\","
                                + "\"cardValidityDateBegin\":\"" + transformDate(id.cardValidityDateBegin) + "\","
                                + "\"cardValidityDateEnd\":\"" + transformDate(id.cardValidityDateEnd) + "\","
                                + "\"cardDeliveryMunicipality\":\"" + id.cardDeliveryMunicipality + "\","
                                + "\"nationalNumber\":\"" + id.nationalNumber + "\","
                                + "\"firstname\":\"" + id.firstName + "\","
                                + "\"name\":\""+id.name+"\","
                                + "\"middleName\":\""+id.middleName+"\","
                                + "\"nationality\":\""+id.nationality+"\","
                                + "\"placeOfBirth\":\""+id.placeOfBirth+"\","
                                + "\"dateOfBirth\":\""+transformDate(id.dateOfBirth)+"\","
                                + "\"gender\":\""+id.gender+"\","
                                + "\"nobleCondition\":\""+id.nobleCondition+"\","
                                + "\"documentType\":\""+id.documentType+"\","
                                + "\"specialStatus\":\""+id.specialStatus+"\","
                                + "\"photoBase64\":\""+image+"\","
                                + "\"duplicate\":\""+id.duplicate+"\","
                                + "\"specialOrganisation\":\""+id.specialOrganisation+"\","
                                + "\"memberOfFamily\":\""+id.memberOfFamily+"\","
                                //+ "\"data\":\""+Arrays.toString(id.data)+"\","
                                + "\"streetAndNumber\":\""+address.streetAndNumber+"\","
                                + "\"zip\":\""+address.zip+"\","
                                + "\"municipality\":\""+address.municipality+"\""
                                //+ "\"addressData\":\""+Arrays.toString(address.data)+"\""
                            + "}"
                        + "}");
            } catch (final CardException cex) {
                    // TODO Auto-generated catch block
                    cex.printStackTrace();
            } catch (final IOException iox) {
                    // TODO Auto-generated catch block
                    iox.printStackTrace();
            }

	}
        
        public static String transformDate(final Calendar calendar) {
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
