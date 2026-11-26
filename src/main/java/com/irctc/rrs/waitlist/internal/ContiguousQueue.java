package com.irctc.rrs.waitlist.internal;

import com.irctc.rrs.waitlist.WaitlistService.QueueType;
import com.irctc.rrs.waitlist.WaitlistService.ShiftedEntry;
import com.irctc.rrs.waitlist.WaitlistService.WaitlistEntrySnapshot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Maintains strictly contiguous 1..N numbering for RAC and WL queues on every mutation.
 */
public class ContiguousQueue {

    public static class MutableEntry {
        private final String ticketId;
        private final String pnrNumber;
        private final String passengerName;
        private QueueType queueType;
        private int contiguousPosition;
        private String allocatedCoach;
        private Integer allocatedBerth;
        private final Instant enqueuedAt;

        public MutableEntry(
                String ticketId,
                String pnrNumber,
                String passengerName,
                QueueType queueType,
                int contiguousPosition,
                String allocatedCoach,
                Integer allocatedBerth,
                Instant enqueuedAt
        ) {
            this.ticketId = ticketId;
            this.pnrNumber = pnrNumber;
            this.passengerName = passengerName;
            this.queueType = queueType;
            this.contiguousPosition = contiguousPosition;
            this.allocatedCoach = allocatedCoach;
            this.allocatedBerth = allocatedBerth;
            this.enqueuedAt = enqueuedAt;
        }

        public String getTicketId() {
            return ticketId;
        }

        public String getPnrNumber() {
            return pnrNumber;
        }

        public String getPassengerName() {
            return passengerName;
        }

        public QueueType getQueueType() {
            return queueType;
        }

        public int getContiguousPosition() {
            return contiguousPosition;
        }

        public String getAllocatedCoach() {
            return allocatedCoach;
        }

        public Integer getAllocatedBerth() {
            return allocatedBerth;
        }

        public void promoteToRac(int position, String coach, Integer berth) {
            this.queueType = QueueType.RAC;
            this.contiguousPosition = position;
            this.allocatedCoach = coach;
            this.allocatedBerth = berth;
        }

        public void updateRacBerth(String coach, Integer berth) {
            this.allocatedCoach = coach;
            this.allocatedBerth = berth;
        }

        public WaitlistEntrySnapshot toSnapshot() {
            return new WaitlistEntrySnapshot(
                    ticketId, pnrNumber, passengerName, queueType, contiguousPosition, allocatedCoach, allocatedBerth, enqueuedAt
            );
        }
    }

    private final LinkedList<MutableEntry> racEntries = new LinkedList<>();
    private final LinkedList<MutableEntry> wlEntries = new LinkedList<>();

    public WaitlistEntrySnapshot addRac(
            String ticketId,
            String pnrNumber,
            String passengerName,
            String coach,
            Integer berth,
            Instant now
    ) {
        MutableEntry entry = new MutableEntry(
                ticketId, pnrNumber, passengerName, QueueType.RAC, racEntries.size() + 1, coach, berth, now
        );
        racEntries.addLast(entry);
        return entry.toSnapshot();
    }

    public WaitlistEntrySnapshot addWaitlist(
            String ticketId,
            String pnrNumber,
            String passengerName,
            Instant now
    ) {
        MutableEntry entry = new MutableEntry(
                ticketId, pnrNumber, passengerName, QueueType.WL, wlEntries.size() + 1, null, null, now
        );
        wlEntries.addLast(entry);
        return entry.toSnapshot();
    }

    public MutableEntry popFirstRac(List<ShiftedEntry> shifts) {
        if (racEntries.isEmpty()) {
            return null;
        }
        MutableEntry winner = racEntries.removeFirst();
        reindex(racEntries, QueueType.RAC, shifts);
        return winner;
    }

    public MutableEntry popFirstWaitlist(List<ShiftedEntry> shifts) {
        if (wlEntries.isEmpty()) {
            return null;
        }
        MutableEntry winner = wlEntries.removeFirst();
        reindex(wlEntries, QueueType.WL, shifts);
        return winner;
    }

    public MutableEntry appendPromotedWaitlistToRac(MutableEntry wlWinner, String coach, Integer berth) {
        int newPos = racEntries.size() + 1;
        wlWinner.promoteToRac(newPos, coach, berth);
        racEntries.addLast(wlWinner);
        return wlWinner;
    }

    public MutableEntry removeRacByTicketId(String ticketId, List<ShiftedEntry> shifts) {
        var it = racEntries.iterator();
        MutableEntry removed = null;
        while (it.hasNext()) {
            MutableEntry e = it.next();
            if (e.getTicketId().equals(ticketId)) {
                it.remove();
                removed = e;
                break;
            }
        }
        if (removed != null) {
            reindex(racEntries, QueueType.RAC, shifts);
        }
        return removed;
    }

    public boolean removeWaitlistByTicketId(String ticketId, List<ShiftedEntry> shifts) {
        var it = wlEntries.iterator();
        boolean removed = false;
        while (it.hasNext()) {
            MutableEntry e = it.next();
            if (e.getTicketId().equals(ticketId)) {
                it.remove();
                removed = true;
                break;
            }
        }
        if (removed) {
            reindex(wlEntries, QueueType.WL, shifts);
        }
        return removed;
    }

    public List<WaitlistEntrySnapshot> drainWaitlist() {
        List<WaitlistEntrySnapshot> drained = wlEntries.stream().map(MutableEntry::toSnapshot).toList();
        wlEntries.clear();
        return drained;
    }

    public List<WaitlistEntrySnapshot> snapshotRac() {
        return racEntries.stream().map(MutableEntry::toSnapshot).toList();
    }

    public List<WaitlistEntrySnapshot> snapshotWaitlist() {
        return wlEntries.stream().map(MutableEntry::toSnapshot).toList();
    }

    public boolean isStrictlyContiguous() {
        return isSequential(racEntries) && isSequential(wlEntries);
    }

    private boolean isSequential(List<MutableEntry> list) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getContiguousPosition() != i + 1) {
                return false;
            }
        }
        return true;
    }

    private void reindex(List<MutableEntry> entries, QueueType type, List<ShiftedEntry> shifts) {
        for (int i = 0; i < entries.size(); i++) {
            MutableEntry e = entries.get(i);
            int expected = i + 1;
            if (e.contiguousPosition != expected) {
                shifts.add(new ShiftedEntry(e.ticketId, e.pnrNumber, type, e.contiguousPosition, expected));
                e.contiguousPosition = expected;
            }
        }
    }
}
