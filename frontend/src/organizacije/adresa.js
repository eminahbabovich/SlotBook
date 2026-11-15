// Prijedlog adrese (sluga) iz naziva, po pravilu iz CreateOrganizationRequest:
// mala slova bez kvačica, brojevi i crtice između riječi, najviše 60 znakova.
// NFD rastavi č na c i kvačicu, pa se kvačica obriše. đ se ne rastavlja, pa ide posebno.
export function predloziAdresu(naziv) {
	return naziv
		.toLowerCase()
		.replaceAll('đ', 'dj')
		.normalize('NFD')
		.replace(/[̀-ͯ]/g, '')
		.replace(/[^a-z0-9]+/g, '-')
		.slice(0, 60)
		.replace(/^-+|-+$/g, '')
}
