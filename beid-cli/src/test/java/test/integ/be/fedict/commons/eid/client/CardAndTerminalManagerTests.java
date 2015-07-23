/*
 * Commons eID Project.
 * Copyright (C) 2008-2013 FedICT.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License version
 * 3.0 as published by the Free Software Foundation.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, see 
 * http://www.gnu.org/licenses/.
 */

/**
 * Manual exercise for CardAndTerminalManager.
 * Prints events and list of readers with cards.
 * [short readername] ... 
 * readers with cards inserted have a "*" behind their short name
 * 
 * @author Frank Marien
 * 
 */

package test.integ.be.fedict.commons.eid.client;
import static org.junit.Assert.assertEquals;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import javax.smartcardio.ATR;
import javax.smartcardio.Card;
import javax.smartcardio.CardTerminal;
import org.junit.Before;
import org.junit.Test;
import test.integ.be.fedict.commons.eid.client.simulation.SimulatedCard;
import test.integ.be.fedict.commons.eid.client.simulation.SimulatedCardTerminal;
import test.integ.be.fedict.commons.eid.client.simulation.SimulatedCardTerminals;
import be.fedict.commons.eid.client.CardAndTerminalManager;
import be.fedict.commons.eid.client.event.CardEventsListener;
import be.fedict.commons.eid.client.event.CardTerminalEventsListener;

public class CardAndTerminalManagerTests {
	private static final int numberOfTerminals = 16;
	private static final int numberOfCards = 16;

	private List<SimulatedCard> simulatedBeIDCard;
	private List<SimulatedCardTerminal> simulatedCardTerminal;
	private SimulatedCardTerminals simulatedCardTerminals;

	@Before
	public void setUp() {
		this.simulatedBeIDCard = new ArrayList<SimulatedCard>(numberOfCards);
		for (int i = 0; i < numberOfCards; i++) {
			this.simulatedBeIDCard.add(new SimulatedCard(new ATR(new byte[]{
					0x3b, (byte) 0x98, (byte) i, 0x40, (byte) i, (byte) i,
					(byte) i, (byte) i, 0x01, 0x01, (byte) 0xad, 0x13, 0x10})));
		}
		this.simulatedCardTerminal = new ArrayList<SimulatedCardTerminal>(
				numberOfTerminals);
		for (int i = 0; i < numberOfTerminals; i++) {
			this.simulatedCardTerminal.add(new SimulatedCardTerminal(
					"Fedix SCR " + i));
		}

		this.simulatedCardTerminals = new SimulatedCardTerminals();
	}

	// ---------------------------------------------------------------------------------------------

	private class RecordKeepingCardTerminalEventsListener
			implements
				CardTerminalEventsListener {
		private Set<CardTerminal> recordedState;

		public RecordKeepingCardTerminalEventsListener() {
			super();
			this.recordedState = new HashSet<CardTerminal>();
		}

		@Override
		public synchronized void terminalAttached(
				final CardTerminal cardTerminal) {
			this.recordedState.add(cardTerminal);

		}

		@Override
		public synchronized void terminalDetached(
				final CardTerminal cardTerminal) {
			this.recordedState.remove(cardTerminal);

		}

		public synchronized Set<CardTerminal> getRecordedState() {
			return new HashSet<CardTerminal>(this.recordedState);
		}

		@Override
		public void terminalEventsInitialized() {
			// TODO Auto-generated method stub

		}
	}

	private class RecordKeepingCardEventsListener implements CardEventsListener {
		private Map<CardTerminal, Card> recordedState;

		public RecordKeepingCardEventsListener() {
			super();
			this.recordedState = new HashMap<CardTerminal, Card>();
		}

		@Override
		public synchronized void cardInserted(final CardTerminal cardTerminal,
				final Card card) {
			if (this.recordedState.containsKey(cardTerminal)) {
				throw new IllegalStateException(
						"Cannot Insert 2 Cards in 1 CardTerminal");
			}
			this.recordedState.put(cardTerminal, card);
		}

		@Override
		public synchronized void cardRemoved(final CardTerminal cardTerminal) {
			if (!this.recordedState.containsKey(cardTerminal)) {
				throw new IllegalStateException(
						"Cannot Remove Card That is not There");
			}
			this.recordedState.remove(cardTerminal);
		}

		public synchronized Map<CardTerminal, Card> getRecordedState() {
			return this.recordedState;
		}

		@Override
		public void cardEventsInitialized() {
			// TODO Auto-generated method stub

		}
	}

	@Test
	public void testTerminalAttachDetachDetection() throws Exception {
		final Random random = new Random(0);
		final Set<CardTerminal> expectedState = new HashSet<CardTerminal>();
		final CardAndTerminalManager cardAndTerminalManager = new CardAndTerminalManager(
				new TestLogger(), this.simulatedCardTerminals);
		final RecordKeepingCardTerminalEventsListener recorder = new RecordKeepingCardTerminalEventsListener();
		cardAndTerminalManager.addCardTerminalListener(recorder);
		cardAndTerminalManager
				.addCardTerminalListener(new NPEProneCardTerminalEventsListener());
		cardAndTerminalManager.start();

		System.err
				.println("attaching and detaching some simulated cardterminals");

		final ArrayList<SimulatedCardTerminal> terminalsToExercise = new ArrayList<SimulatedCardTerminal>(
				this.simulatedCardTerminal);
		final Set<SimulatedCardTerminal> detachedTerminalSet = new HashSet<SimulatedCardTerminal>(
				terminalsToExercise);
		final Set<SimulatedCardTerminal> attachedTerminalSet = new HashSet<SimulatedCardTerminal>();

		for (int i = 0; i < 1000; i++) {
			for (int j = 0; j < random.nextInt(numberOfTerminals); j++) {
				final SimulatedCardTerminal terminalToAttach = terminalsToExercise
						.get(random.nextInt(numberOfTerminals));
				if (detachedTerminalSet.contains(terminalToAttach)) {
					expectedState.add(terminalToAttach);
					this.simulatedCardTerminals
							.attachCardTerminal(terminalToAttach);
					detachedTerminalSet.remove(terminalToAttach);
					attachedTerminalSet.add(terminalToAttach);
					System.out.println("attached ["
							+ terminalToAttach.getName() + "]");
					StringUtils.printTerminalSet(expectedState);
					StringUtils.printTerminalSet(recorder.getRecordedState());
				}
			}

			for (int j = 0; j < random.nextInt(numberOfTerminals); j++) {
				final SimulatedCardTerminal terminalToDetach = terminalsToExercise
						.get(random.nextInt(numberOfTerminals));
				if (attachedTerminalSet.contains(terminalToDetach)) {
					expectedState.remove(terminalToDetach);
					this.simulatedCardTerminals
							.detachCardTerminal(terminalToDetach);
					detachedTerminalSet.add(terminalToDetach);
					attachedTerminalSet.remove(terminalToDetach);
					System.out.println("detached ["
							+ terminalToDetach.getName() + "]");
					StringUtils.printTerminalSet(expectedState);
					StringUtils.printTerminalSet(recorder.getRecordedState());
				}
			}
		}

		Thread.sleep(1000);
		cardAndTerminalManager.stop();
		assertEquals(expectedState, recorder.getRecordedState());
	}

	@Test
	public void testCardInsertRemoveDetection() throws Exception {
		final Random random = new Random(0);
		final Map<SimulatedCardTerminal, SimulatedCard> expectedState = new HashMap<SimulatedCardTerminal, SimulatedCard>();
		final CardAndTerminalManager cardAndTerminalManager = new CardAndTerminalManager(
				new TestLogger(), this.simulatedCardTerminals);
		final RecordKeepingCardEventsListener recorder = new RecordKeepingCardEventsListener();
		cardAndTerminalManager.addCardListener(recorder);
		cardAndTerminalManager
				.addCardListener(new NPEProneCardEventsListener());
		cardAndTerminalManager.start();

		final ArrayList<SimulatedCardTerminal> terminalsToExercise = new ArrayList<SimulatedCardTerminal>(
				this.simulatedCardTerminal);
		final Set<SimulatedCardTerminal> emptyTerminalSet = new HashSet<SimulatedCardTerminal>(
				terminalsToExercise);
		final Set<SimulatedCardTerminal> fullTerminalSet = new HashSet<SimulatedCardTerminal>();

		final ArrayList<SimulatedCard> cardsToExercise = new ArrayList<SimulatedCard>(
				this.simulatedBeIDCard);
		final Set<SimulatedCard> unusedCardSet = new HashSet<SimulatedCard>(
				cardsToExercise);
		final Set<SimulatedCard> usedCardSet = new HashSet<SimulatedCard>();

		System.err.println("attaching some simulated card readers");

		// attach all simulated CardTerminals
		for (SimulatedCardTerminal terminal : emptyTerminalSet) {
			this.simulatedCardTerminals.attachCardTerminal(terminal);
		}

		System.err.println("inserting and removing some simulated cards");

		for (int i = 0; i < 1000; i++) {
			for (int j = 0; j < random.nextInt(numberOfCards); j++) {
				final SimulatedCardTerminal terminalToInsertCardInto = terminalsToExercise
						.get(random.nextInt(numberOfTerminals));
				final SimulatedCard cardToInsert = cardsToExercise.get(random
						.nextInt(numberOfCards));

				if (emptyTerminalSet.contains(terminalToInsertCardInto)
						&& unusedCardSet.contains(cardToInsert)) {
					expectedState.put(terminalToInsertCardInto, cardToInsert);
					terminalToInsertCardInto.insertCard(cardToInsert);
					emptyTerminalSet.remove(terminalToInsertCardInto);
					fullTerminalSet.add(terminalToInsertCardInto);
					unusedCardSet.remove(cardToInsert);
					usedCardSet.add(cardToInsert);
					System.out.println("inserted ["
							+ StringUtils.atrToString(cardToInsert.getATR())
							+ "] into [" + terminalToInsertCardInto.getName()
							+ "]");
				}
			}

			for (int j = 0; j < random.nextInt(numberOfCards); j++) {
				final SimulatedCardTerminal terminalToRemoveCardFrom = terminalsToExercise
						.get(random.nextInt(numberOfTerminals));
				final SimulatedCard cardToRemove = expectedState
						.get(terminalToRemoveCardFrom);

				if (fullTerminalSet.contains(terminalToRemoveCardFrom)
						&& usedCardSet.contains(cardToRemove)) {
					expectedState.remove(terminalToRemoveCardFrom);
					terminalToRemoveCardFrom.removeCard();
					emptyTerminalSet.add(terminalToRemoveCardFrom);
					fullTerminalSet.remove(terminalToRemoveCardFrom);
					usedCardSet.remove(cardToRemove);
					unusedCardSet.add(cardToRemove);
					System.out.println("removed ["
							+ StringUtils.atrToString(cardToRemove.getATR())
							+ "] from [" + terminalToRemoveCardFrom.getName()
							+ "]");
				}
			}
		}

		Thread.sleep(1000);
		cardAndTerminalManager.stop();
		assertEquals(expectedState, recorder.getRecordedState());
	}

	private final class NPEProneCardTerminalEventsListener
			implements
				CardTerminalEventsListener {
		@Override
		public void terminalAttached(final CardTerminal cardTerminal) {
			throw new NullPointerException(
					"Fake NPE attempting to trash a CardTerminalEventsListener");
		}

		@Override
		public void terminalDetached(final CardTerminal cardTerminal) {
			throw new NullPointerException(
					"Fake NPE attempting to trash a CardTerminalEventsListener");
		}

		@Override
		public void terminalEventsInitialized() {
			System.out.println("Terminal Events Initialised");
		}
	}

	private final class NPEProneCardEventsListener
			implements
				CardEventsListener {
		@Override
		public void cardInserted(final CardTerminal cardTerminal,
				final Card card) {
			throw new NullPointerException(
					"Fake NPE attempting to trash a CardEventsListener");
		}

		@Override
		public void cardRemoved(final CardTerminal cardTerminal) {
			throw new NullPointerException(
					"Fake NPE attempting to trash a CardEventsListener");
		}

		@Override
		public void cardEventsInitialized() {
			System.out.println("Card Events Initialised");
		}
	}
}
