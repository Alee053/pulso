// frontend/src/app/features/discovery/data-access/profile-api.ts

import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, type Observable } from 'rxjs';
import { appConfig } from '../../../core/config/app-config';
import type { Profile } from '../domain/profile';

interface ProfileApiResponse {
    id: string;
    nombre_visible: string;
    biografia: string | null;
    ciudad: string;
    departamento: string;
    correo?: string;
    avatar_url?: string;
    updated_at?: string;
}

@Injectable({ providedIn: 'root' })
export class ProfileApi {
    private readonly http = inject(HttpClient);

    getProfile(userId: string): Observable<Profile> {
        return this.http
            .get<ProfileApiResponse>(`${appConfig.apiBaseUrl}/api/profile/${userId}`)
            .pipe(map((response) => this.mapFromApi(response)));
    }

    updateProfile(profile: Profile): Observable<Profile> {
        return this.http
            .put<ProfileApiResponse>(`${appConfig.apiBaseUrl}/api/profile/${profile.id}`, {
                nombre_visible: profile.visibleName,
                biografia: profile.bio || null,
                ciudad: profile.city,
                departamento: profile.department
            })
            .pipe(map((response) => this.mapFromApi(response)));
    }

    private mapFromApi(response: ProfileApiResponse): Profile {
        return {
            id: response.id,
            visibleName: response.nombre_visible,
            bio: response.biografia || '',
            city: response.ciudad,
            department: response.departamento,
            email: response.correo,
            avatarUrl: response.avatar_url,
            updatedAt: response.updated_at ? new Date(response.updated_at) : undefined
        };
    }
}