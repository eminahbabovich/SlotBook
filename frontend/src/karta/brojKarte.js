// Broj karte je početak ID-ja rezervacije (UUID), pa ga osoblje može naći u bazi. Dva dijela po
// četiri znaka se lakše pročitaju naglas na šalteru nego osam zaredom.
export function brojKarte(id) {
	return id.slice(0, 8).toUpperCase().replace(/^(.{4})(.{4})$/, '$1-$2')
}
