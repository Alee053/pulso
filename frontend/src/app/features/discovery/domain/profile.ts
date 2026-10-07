// frontend/src/app/features/discovery/domain/profile.ts

export interface Profile {
    id: string;
    visibleName: string;
    bio: string;
    city: string;
    department: string;
    email?: string;
    avatarUrl?: string;
    updatedAt?: Date;
}

export const ProfileValidation = {
    visibleName: {
        minLength: 2,
        maxLength: 50
    },
    bio: {
        maxLength: 200
    },
    city: {
        maxLength: 100
    },
    department: {
        maxLength: 100
    }
} as const;