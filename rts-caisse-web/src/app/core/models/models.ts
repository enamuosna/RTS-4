// ============================================================
//  Types miroirs des DTOs Spring Boot (sn.rts.caisse.dto)
// ============================================================

export type Role = 'ADMIN' | 'SUPERVISEUR' | 'CAISSIER';

export type TypeOperation = 'ENTREE' | 'SORTIE';

export type ModePaiement =
  | 'ESPECES'
  | 'CHEQUE'
  | 'VIREMENT'
  | 'CARTE_BANCAIRE'
  | 'WAVE'
  | 'ORANGE_MONEY'
  | 'FREE_MONEY';

export type StatutCaisse = 'FERMEE' | 'OUVERTE' | 'SUSPENDUE';

// ---------- Auth ----------
export interface LoginRequest {
  login: string;
  motDePasse: string;
}

export interface AuthResponse {
  token: string;
  type: string;
  expiresInMs: number;
  utilisateurId: number;
  matricule: string;
  login: string;
  nomComplet: string;
  role: Role;
}

export interface RegisterRequest {
  matricule: string;
  login: string;
  motDePasse: string;
  prenom: string;
  nom: string;
  email?: string;
  telephone?: string;
  role: Role;
}

// ---------- Utilisateur ----------
export interface Utilisateur {
  id: number;
  matricule: string;
  login: string;
  prenom: string;
  nom: string;
  email?: string;
  telephone?: string;
  role: Role;
  actif: boolean;
}

// ---------- Caisse ----------
export interface Caisse {
  id: number;
  code: string;
  libelle: string;
  emplacement?: string;
  statut: StatutCaisse;
  soldeCourant: number;
  caissierId?: number;
  caissierNomComplet?: string;
}

// ---------- Catégorie ----------
export interface CategorieOperation {
  id: number;
  code: string;
  libelle: string;
  typeOperation: TypeOperation;
  actif: boolean;
}

// ---------- Client ----------
export interface Client {
  id: number;
  raisonSociale: string;
  identifiantFiscal?: string;
  telephone?: string;
  email?: string;
  adresse?: string;
  actif: boolean;
}

// ---------- Opération ----------
export interface OperationCaisseRequest {
  caisseId: number;
  categorieId: number;
  clientId?: number;
  typeOperation: TypeOperation;
  montant: number;
  modePaiement: ModePaiement;
  motif: string;
  reference?: string;
}

export interface OperationCaisse {
  id: number;
  numeroRecu: string;
  typeOperation: TypeOperation;
  montant: number;
  motif: string;
  modePaiement: ModePaiement;
  reference?: string;
  dateOperation: string;
  caisseId: number;
  caisseLibelle: string;
  caissierId: number;
  caissierNomComplet: string;
  categorieId: number;
  categorieLibelle: string;
  clientId?: number;
  clientRaisonSociale?: string;
  annulee: boolean;
  motifAnnulation?: string;
}

// ---------- Journal ----------
export interface OuvertureCaisseRequest {
  fondOuverture: number;
}

export interface ClotureCaisseRequest {
  soldeReel: number;
  commentaire?: string;
}

export interface JournalCaisse {
  id: number;
  dateJournal: string;
  caisseId: number;
  caisseLibelle: string;
  caissierId: number;
  caissierNomComplet: string;
  fondOuverture: number;
  totalEntrees: number;
  totalSorties: number;
  soldeTheorique: number;
  soldeReel?: number;
  ecart?: number;
  commentaire?: string;
  ouvertLe: string;
  clotureLe?: string;
  cloture: boolean;
  valideeParId?: number;
  valideeParNom?: string;
}

// ---------- Dashboard ----------
export interface LigneCaisse {
  caisseId: number;
  codeCaisse: string;
  libelleCaisse: string;
  entrees: number;
  sorties: number;
  solde: number;
}

export interface LigneCategorie {
  categorieId: number;
  codeCategorie: string;
  libelleCategorie: string;
  typeOperation: string;
  montantTotal: number;
  nombre: number;
}

export interface DashboardResponse {
  date: string;
  totalEntreesJour: number;
  totalSortiesJour: number;
  soldeNetJour: number;
  nombreOperations: number;
  nombreCaissesOuvertes: number;
  repartitionParCaisse: LigneCaisse[];
  repartitionParCategorie: LigneCategorie[];
}

// ---------- Pagination Spring ----------
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}
