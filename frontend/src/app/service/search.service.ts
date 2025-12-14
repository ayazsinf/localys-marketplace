import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class SearchService {

    private readonly searchTermSubject = new BehaviorSubject<string>('');
    readonly searchTerm$ = this.searchTermSubject.asObservable();

    setSearchTerm(term: string): void {
        this.searchTermSubject.next(term);
    }

    clear(): void {
        this.searchTermSubject.next('');
    }
}
