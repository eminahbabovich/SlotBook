import { createContext, useContext } from 'react'

export const SesijaContext = createContext(null)

// { status, korisnik, prijavi, registruj, odjavi, pokusajPonovo }
// status: 'provjerava' | 'odjavljen' | 'prijavljen' | 'nedostupan'
export function useSesija() {
	const sesija = useContext(SesijaContext)
	if (sesija === null) {
		throw new Error('useSesija radi samo unutar SesijaProvider')
	}
	return sesija
}
