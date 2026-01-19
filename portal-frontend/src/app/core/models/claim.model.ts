export interface ClaimSummary {
  id: string;
  referenceNumber: string;
  status: ClaimStatus;
  type: ClaimType;
  policyholderName: string;
  insurerName: string;
  estimatedDamage?: number;
  currency?: string;
  incidentDate: string;
  createdAt: string;
}

export interface ClaimDetail {
  id: string;
  referenceNumber: string;
  status: ClaimStatus;
  availableTransitions: ClaimStatus[];
  type: ClaimType;
  description?: string;
  incidentDate: string;
  estimatedDamage?: number;
  currency?: string;
  location?: Location;
  policyholder: Actor;
  insurer: Actor;
  expertAssignments: ExpertAssignment[];
  comments: Comment[];
  history: Event[];
  createdAt: string;
  updatedAt: string;
}

export interface Actor {
  id: string;
  name: string;
  type?: string;
  email?: string;
  phone?: string;
  address?: string;
}

export interface Location {
  address?: string;
  latitude?: number;
  longitude?: number;
}

export interface ExpertAssignment {
  id: string;
  expert: Actor;
  scheduledDate?: string;
  notes?: string;
  assignedAt: string;
}

export interface Comment {
  id: string;
  content: string;
  authorId: string;
  authorType: string;
  authorName?: string;
  createdAt: string;
}

export interface Event {
  id: string;
  eventType: string;
  description?: string;
  oldValue?: string;
  newValue?: string;
  actorId?: string;
  actorName?: string;
  occurredAt: string;
}

export type ClaimStatus =
  | 'DECLARED'
  | 'UNDER_REVIEW'
  | 'QUALIFIED'
  | 'IN_PROGRESS'
  | 'CLOSED'
  | 'ABANDONED';

export type ClaimType =
  | 'WATER_DAMAGE'
  | 'FIRE'
  | 'THEFT'
  | 'LIABILITY'
  | 'PROPERTY_DAMAGE'
  | 'NATURAL_DISASTER'
  | 'OTHER';

export const CLAIM_STATUS_LABELS: Record<ClaimStatus, string> = {
  DECLARED: 'Declared',
  UNDER_REVIEW: 'Under Review',
  QUALIFIED: 'Qualified',
  IN_PROGRESS: 'In Progress',
  CLOSED: 'Closed',
  ABANDONED: 'Abandoned'
};

export const CLAIM_TYPE_LABELS: Record<ClaimType, string> = {
  WATER_DAMAGE: 'Water Damage',
  FIRE: 'Fire',
  THEFT: 'Theft',
  LIABILITY: 'Liability',
  PROPERTY_DAMAGE: 'Property Damage',
  NATURAL_DISASTER: 'Natural Disaster',
  OTHER: 'Other'
};

export const CLAIM_STATUS_COLORS: Record<ClaimStatus, string> = {
  DECLARED: '#1976d2',
  UNDER_REVIEW: '#ffb300',
  QUALIFIED: '#ff9800',
  IN_PROGRESS: '#9c27b0',
  CLOSED: '#4caf50',
  ABANDONED: '#9e9e9e'
};

export interface CreateClaimRequest {
  policyholderId: string;
  insurerId: string;
  type: ClaimType;
  description?: string;
  incidentDate: string;
  location?: Location;
  estimatedDamage?: number;
  currency?: string;
}

export interface StatusUpdateRequest {
  status: ClaimStatus;
  reason?: string;
}

export interface ExpertAssignmentRequest {
  expertId: string;
  scheduledDate?: string;
  notes?: string;
}

export interface CommentRequest {
  content: string;
  authorId: string;
  authorType: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
