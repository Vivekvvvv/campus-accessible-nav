import { describe, it, expect, beforeEach, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useRouteStore } from '@/stores/useRouteStore';
import { apiRequest } from '@/services/apiService';

// Mock dependent stores
vi.mock('@/stores/useToastStore', () => ({
  useToastStore: () => ({
    push: vi.fn(),
  }),
}));

vi.mock('@/stores/useMapStore', () => ({
  useMapStore: () => ({
    campusOnly: true,
  }),
}));

vi.mock('@/services/apiService', () => ({
  apiRequest: vi.fn(),
}));

describe('useRouteStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  describe('setPoint', () => {
    it('should set start point correctly', () => {
      const store = useRouteStore();
      const point = { lat: 23.275, lng: 113.200, name: 'Test Start' };

      store.setPoint('start', point);

      expect(store.points.start).toEqual(point);
      expect(store.points.end).toBeNull();
    });

    it('should set end point correctly', () => {
      const store = useRouteStore();
      const point = { lat: 23.276, lng: 113.201, name: 'Test End' };

      store.setPoint('end', point);

      expect(store.points.end).toEqual(point);
      expect(store.points.start).toBeNull();
    });
  });

  describe('clearPoints', () => {
    it('should clear both points and route state', () => {
      const store = useRouteStore();

      store.setPoint('start', { lat: 23.275, lng: 113.200 });
      store.setPoint('end', { lat: 23.276, lng: 113.201 });
      store.clearPoints();

      expect(store.points.start).toBeNull();
      expect(store.points.end).toBeNull();
      expect(store.routeState.walk).toBeNull();
      expect(store.routeState.wheel).toBeNull();
    });
  });

  describe('setActiveMode', () => {
    it('should set active mode to walk', () => {
      const store = useRouteStore();

      store.setActiveMode('walk');

      expect(store.routeState.activeMode).toBe('walk');
    });

    it('should set active mode to wheel', () => {
      const store = useRouteStore();

      store.setActiveMode('wheel');

      expect(store.routeState.activeMode).toBe('wheel');
    });
  });

  describe('hasRoute', () => {
    it('should return false when no routes', () => {
      const store = useRouteStore();

      expect(store.hasRoute).toBe(false);
    });

    it('should return true when walk route exists', () => {
      const store = useRouteStore();
      store.routeState.walk = { type: 'FeatureCollection', features: [] } as any;

      expect(store.hasRoute).toBe(true);
    });

    it('should return true when wheel route exists', () => {
      const store = useRouteStore();
      store.routeState.wheel = { type: 'FeatureCollection', features: [] } as any;

      expect(store.hasRoute).toBe(true);
    });
  });

  describe('activeRoute', () => {
    it('should return walk route when mode is walk', () => {
      const store = useRouteStore();
      const walkRoute = { type: 'FeatureCollection', features: [], mode: 'WALK' };
      store.routeState.walk = walkRoute as any;
      store.routeState.activeMode = 'walk';

      expect(store.activeRoute).toBe(walkRoute);
    });

    it('should return wheel route when mode is wheel', () => {
      const store = useRouteStore();
      const wheelRoute = { type: 'FeatureCollection', features: [], mode: 'WHEELCHAIR' };
      store.routeState.wheel = wheelRoute as any;
      store.routeState.activeMode = 'wheel';

      expect(store.activeRoute).toBe(wheelRoute);
    });
  });

  describe('computeRoutes', () => {
    it('should not compute routes when start point is missing', async () => {
      const store = useRouteStore();
      store.setPoint('end', { lat: 23.276, lng: 113.201 });

      await store.computeRoutes();

      expect(apiRequest).not.toHaveBeenCalled();
      expect(store.routeState.loading).toBe(false);
    });

    it('should not compute routes when end point is missing', async () => {
      const store = useRouteStore();
      store.setPoint('start', { lat: 23.275, lng: 113.200 });

      await store.computeRoutes();

      expect(apiRequest).not.toHaveBeenCalled();
      expect(store.routeState.loading).toBe(false);
    });

    it('should compute routes when both points are set', async () => {
      const store = useRouteStore();
      const mockRouteResponse = {
        path: [
          { lat: 23.275, lng: 113.200 },
          { lat: 23.276, lng: 113.201 },
        ],
        distanceM: 150,
        durationSec: 120,
        riskCount: 0,
        instructions: [],
      };

      (apiRequest as ReturnType<typeof vi.fn>).mockResolvedValue({
        ok: true,
        json: () => Promise.resolve(mockRouteResponse),
      });

      store.setPoint('start', { lat: 23.275, lng: 113.200 });
      store.setPoint('end', { lat: 23.276, lng: 113.201 });

      await store.computeRoutes();

      expect(apiRequest).toHaveBeenCalledTimes(2); // WALK and WHEELCHAIR
      expect(store.routeState.loading).toBe(false);
      expect(store.routeState.walk).not.toBeNull();
      expect(store.routeState.wheel).not.toBeNull();
    });

    it('should handle route calculation error', async () => {
      const store = useRouteStore();

      (apiRequest as ReturnType<typeof vi.fn>).mockResolvedValue({
        ok: false,
        status: 500,
        json: () => Promise.resolve({ message: 'Internal server error' }),
        arrayBuffer: () => Promise.resolve(new TextEncoder().encode('Internal server error').buffer),
      });

      store.setPoint('start', { lat: 23.275, lng: 113.200 });
      store.setPoint('end', { lat: 23.276, lng: 113.201 });

      await store.computeRoutes();

      expect(store.routeState.error).toBeTruthy();
      expect(store.routeState.loading).toBe(false);
    });
  });
});
