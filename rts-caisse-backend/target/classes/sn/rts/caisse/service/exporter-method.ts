// À remplacer dans JournauxComponent (vers la ligne 1416)

exporter(journal: JournalCaisse): void {
  this.exportEnCours.set(journal.id);
  this.service.exporterExcel(journal.id).subscribe({
    next: (response: HttpResponse<Blob>) => {
      this.exportEnCours.set(null);
      const blob = response.body;
      if (!blob) {
        this.snackBar.open("Réponse vide du serveur", "Fermer", {
          duration: 4000,
          panelClass: ['snackbar-error']
        });
        return;
      }
      const nomFichier = this.extraireNomFichier(response)
        ?? `journal-${journal.id}.xlsx`;
      this.sauvegarderBlob(blob, nomFichier);
      this.snackBar.open(`Fichier ${nomFichier} téléchargé`, "OK", {
        duration: 3000,
        panelClass: ['snackbar-success']
      });
    },
    error: (err: HttpErrorResponse) => {
      this.exportEnCours.set(null);
      const message =
        (err.error && typeof err.error === 'object' && 'message' in err.error
          ? (err.error as { message?: string }).message
          : null)
        ?? err.message
        ?? "Échec du téléchargement Excel";
      this.snackBar.open(message, "Fermer", {
        duration: 5000,
        panelClass: ['snackbar-error']
      });
    }
  });
}
