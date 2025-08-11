import { NativeModules } from 'react-native';

const { RNJWPlayerHeadless } = NativeModules;

/**
 * JWPlayer Headless module for handling Android Auto media selection in background mode
 */
export class JWPlayerHeadless {
  /**
   * Get pending media info from headless mode for app restoration
   */
  static async getPendingMediaInfo() {
    try {
      return await RNJWPlayerHeadless.getPendingMediaInfo();
    } catch (error) {
      console.error('Failed to get pending media info:', error);
      return null;
    }
  }

  /**
   * Clear pending media info after handling
   */
  static async clearPendingMedia() {
    try {
      return await RNJWPlayerHeadless.clearPendingMedia();
    } catch (error) {
      console.error('Failed to clear pending media:', error);
      return false;
    }
  }

  /**
   * Check if there's pending media from headless mode
   */
  static async hasPendingMedia() {
    try {
      return await RNJWPlayerHeadless.hasPendingMedia();
    } catch (error) {
      console.error('Failed to check pending media:', error);
      return false;
    }
  }
}

export default JWPlayerHeadless;
