// Stripe Checkout je pravi odlazak sa stranice, ne ruta u aplikaciji. Posebno, da ga test zamijeni,
// jer jsdom ne zna otići na drugu adresu.
export function idiNaStripe(adresa) {
	window.location.assign(adresa)
}
