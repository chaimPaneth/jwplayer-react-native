declare module 'react-native' {
  interface NativeModulesStatic {
    RNJWPlayerHeadless: {
      /**
       * Get pending media info from headless mode for app restoration
       */
      getPendingMediaInfo(): Promise<{
        mediaId: string;
        title: string;
        subtitle: string;
        icon: string;
        extras: Record<string, any>;
      } | null>;

      /**
       * Clear pending media info after handling
       */
      clearPendingMedia(): Promise<boolean>;

      /**
       * Check if there's pending media from headless mode
       */
      hasPendingMedia(): Promise<boolean>;
    };
  }
}

export {};
