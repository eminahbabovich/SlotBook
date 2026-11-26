-- Od V37 termin može imati više mjesta, pa ista sala u isto vrijeme može imati više ponuda, po jednu za
-- svako slobodno mjesto. Indeks iz V34 je dozvoljavao samo jednu, pa je druga ponuda rušila cijeli prolaz
-- posla (23505). Da se ne ponudi više mjesta nego što ih ima, čuva lock resursa u WaitlistService.offerFreed:
-- pod njim se broje rezervacije i ponude zajedno, kao i kod obične rezervacije.
DROP INDEX waitlist_jedna_ponuda;
