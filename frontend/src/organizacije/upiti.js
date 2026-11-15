import { useQuery } from '@tanstack/react-query'
import { mojeOrganizacije } from '../api/organizacije.js'
import { resursiOrganizacije } from '../api/termini.js'

// Isti ključ dijele lista i stranica organizacije, pa se lista učita jednom i čuva u kešu.
export const KLJUC_MOJE_ORGANIZACIJE = ['mojeOrganizacije']

export function useMojeOrganizacije() {
	return useQuery({ queryKey: KLJUC_MOJE_ORGANIZACIJE, queryFn: mojeOrganizacije })
}

export const ULOGE = {
	ADMIN: 'admin',
	PROVIDER: 'vodi termine',
	CLIENT: 'rezerviše termine',
}

// Organizacija se traži u mojoj listi. Ako je nema, ne znamo (i ne treba da znamo) da li ne
// postoji ili korisnik nije član.
export function useOrganizacija(slug) {
	const upit = useMojeOrganizacije()
	return { ...upit, organizacija: upit.data?.find((o) => o.slug === slug) }
}

// Dok organizacija nije poznata, upit čeka (enabled), umjesto da zove /organizations/undefined.
export function useResursi(organizacija) {
	return useQuery({
		queryKey: ['resursi', organizacija?.id],
		queryFn: () => resursiOrganizacije(organizacija.id),
		enabled: organizacija !== undefined,
	})
}
