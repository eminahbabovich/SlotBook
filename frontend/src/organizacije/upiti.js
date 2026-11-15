import { useQuery } from '@tanstack/react-query'
import { mojeOrganizacije } from '../api/organizacije.js'

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
